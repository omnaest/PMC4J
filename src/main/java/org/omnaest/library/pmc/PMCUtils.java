/*******************************************************************************
 * Copyright 2021 Danny Kunz
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package org.omnaest.library.pmc;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.omnaest.library.pmc.cloud.PMCCloudUtils;
import org.omnaest.library.pmc.cloud.PMCCloudUtils.CloudArticle;
import org.omnaest.library.pmc.rest.PMCRestUtils;
import org.omnaest.library.pmc.rest.PMCRestUtils.PMCRestAccessor;
import org.omnaest.library.pmc.rest.PMCRestUtils.PMCRestAccessor.Sort;
import org.omnaest.library.pmc.rest.domain.raw.ArticleResult;
import org.omnaest.library.pmc.rest.domain.raw.ArticleResult.ArticleId;
import org.omnaest.utils.CacheUtils;
import org.omnaest.utils.PredicateUtils;
import org.omnaest.utils.StreamUtils;
import org.omnaest.utils.cache.Cache;
import org.omnaest.utils.cache.Cacheable;
import org.omnaest.utils.element.bi.BiElement;
import org.omnaest.utils.element.cached.CachedElement;
import org.omnaest.utils.exception.handler.ExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PMCUtils implements Cacheable<PMCUtils>
{
    private static Logger    LOG              = LoggerFactory.getLogger(PMCUtils.class);

    private Cache            cache            = CacheUtils.newConcurrentInMemoryCache();
    private ExceptionHandler exceptionHandler = e -> LOG.error("Unexpected exception", e);
    private String           apiKey           = null;
    private String           contactEmail     = null;

    private PMCUtils()
    {
        super();
    }

    public static PMCUtils newInstance()
    {
        return new PMCUtils();
    }

    @Override
    public PMCUtils withCache(Cache cache)
    {
        this.cache = cache;
        return this;
    }

    public PMCUtils withExceptionHandler(ExceptionHandler exceptionHandler)
    {
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    /**
     * Sets the NCBI <code>api_key</code>, raising the request limit from 3 to 10 requests per second.
     *
     * @see PMCRestAccessor#withApiKey(String)
     */
    public PMCUtils withApiKey(String apiKey)
    {
        this.apiKey = apiKey;
        return this;
    }

    /**
     * Sets the contact address NCBI uses to warn about problematic traffic before blocking an IP address. Must be the developer of the calling software.
     *
     * @see PMCRestAccessor#withContactEmail(String)
     */
    public PMCUtils withContactEmail(String contactEmail)
    {
        this.contactEmail = contactEmail;
        return this;
    }

    public static interface Article
    {
        public String getTitle();

        public List<String> getAuthors();

        public Optional<LocalDate> getPublicationDate();

        public Optional<byte[]> resolvePDF();

        public Article resolvePDFIfPresent(Consumer<byte[]> pdfConsumer);

        /**
         * The full text as plain text, as extracted from the JATS XML by PMC. Empty if the article is not part of the open access subset.
         */
        public Optional<String> resolveFullText();

        /**
         * The full text as NISO JATS XML. Empty if the article is not part of the open access subset.
         */
        public Optional<String> resolveXML();

        /**
         * The license as encoded by PMC, e.g. <code>CC0</code> or <code>CC BY-NC</code>. Callers redistributing content must honour it.
         */
        public Optional<String> getLicenseCode();

        /**
         * Whether PMC flags this article as retracted.
         */
        public boolean isRetracted();

        public String getId();

        public boolean hasPDF();

        public Optional<PMCReference> getPMCReference();

        public boolean hasPMCReference();

        public static interface PMCReference
        {
            public String getId();

            public String getLink(LinkType linkType);

            public static enum LinkType implements Function<String, String>
            {
                DEFAULT(link -> link), READER(link -> link + "/?report=reader");

                private Function<String, String> linkModifier;

                private LinkType(Function<String, String> linkModifier)
                {
                    this.linkModifier = linkModifier;

                }

                @Override
                public String apply(String link)
                {
                    return this.linkModifier.apply(link);
                }
            }
        }

    }

    public Stream<Article> searchFor(String query)
    {
        Sort sort = null;
        return this.searchFor(query, sort);
    }

    public Stream<Article> searchFor(String query, Sort sort)
    {
        PMCRestAccessor accessor = PMCRestUtils.getInstance()
                                               .withCache(this.cache)
                                               .withApiKey(this.apiKey)
                                               .withContactEmail(this.contactEmail);
        Supplier<List<String>> supplier = new Supplier<List<String>>() {
            private int page = 0;

            @Override
            public List<String> get()
            {
                return accessor.searchFor(query, this.page++, sort)
                               .getEsearchresult()
                               .getIdlist();
            }
        };
        PMCCloudUtils cloudUtils = PMCCloudUtils.newInstance()
                                                .withCache(this.cache)
                                                .withExceptionHandler(this.exceptionHandler);
        return StreamUtils.fromSupplier(supplier, List::isEmpty)
                          .flatMap(ids ->
                          {
                              // One ESummary request per result page instead of one per article, resolved lazily on the first article that needs it.
                              // Do not wrap this in a Cache#computeIfAbsent: the accessor holds the same Cache and its RestClient already caches by request
                              // URL, and nesting the two made a concurrent in memory cache throw IllegalStateException "Recursive update" whenever the inner
                              // key landed in the bin the outer one was updating.
                              CachedElement<Map<String, ArticleResult>> pageResolver = CachedElement.of(() -> Optional.ofNullable(accessor.getByArticleIds(ids))
                                                                                                                     .map(article -> article.getResult())
                                                                                                                     .orElse(Collections.emptyMap()));
                              return ids.stream()
                                        .map(id -> new ArticleImpl(pageResolver, cloudUtils, id));
                          });
    }

    protected static class ArticleImpl implements Article
    {
        private final String                                 id;
        private final CachedElement<Optional<ArticleResult>> articleResolver;
        private final CachedElement<Optional<CloudArticle>>  cloudArticleResolver;

        private ArticleImpl(CachedElement<Map<String, ArticleResult>> pageResolver, PMCCloudUtils cloudUtils, String id)
        {
            this.id = id;
            this.articleResolver = CachedElement.of(() -> Optional.ofNullable(pageResolver.get()
                                                                                          .get(id)));
            this.cloudArticleResolver = CachedElement.of(() -> cloudUtils.findArticle(id));
        }

        @Override
        public String getTitle()
        {
            return this.articleResolver.get()
                                       .map(ArticleResult::getTitle)
                                       .orElse(null);
        }

        @Override
        public List<String> getAuthors()
        {
            return this.articleResolver.get()
                                       .map(ArticleResult::getAuthors)
                                       .orElse(Collections.emptyList())
                                       .stream()
                                       .map(author -> author.getName())
                                       .collect(Collectors.toList());
        }

        @Override
        public Optional<LocalDate> getPublicationDate()
        {
            return this.articleResolver.get()
                                       .map(ArticleResult::getPubdate)
                                       .flatMap(ArticleImpl::parseDate);
        }

        public static Optional<LocalDate> parseDate(String dateStr)
        {
            return Arrays.asList("yyyy M d", "yyyy M", "yyyy")
                         .stream()
                         .map(pattern ->
                         {
                             try
                             {
                                 return LocalDate.parse(org.omnaest.utils.StringUtils.replaceEach(dateStr, map -> map.put("Jan", 1)
                                                                                                                     .put("Feb", 2)
                                                                                                                     .put("Mar", 3)
                                                                                                                     .put("Apr", 4)
                                                                                                                     .put("May", 5)
                                                                                                                     .put("Jun", 6)
                                                                                                                     .put("Jul", 7)
                                                                                                                     .put("Aug", 8)
                                                                                                                     .put("Sep", 9)
                                                                                                                     .put("Oct", 10)
                                                                                                                     .put("Nov", 11)
                                                                                                                     .put("Dec", 12)),
                                                        DateTimeFormatter.ofPattern(pattern));
                             }
                             catch (Exception e)
                             {
                                 return null;
                             }
                         })
                         .filter(PredicateUtils.notNull())
                         .findFirst();
        }

        @Override
        public Optional<byte[]> resolvePDF()
        {
            return this.cloudArticleResolver.get()
                                            .flatMap(CloudArticle::resolvePDF);
        }

        @Override
        public Optional<String> resolveFullText()
        {
            return this.cloudArticleResolver.get()
                                            .flatMap(CloudArticle::resolveText);
        }

        @Override
        public Optional<String> resolveXML()
        {
            return this.cloudArticleResolver.get()
                                            .flatMap(CloudArticle::resolveXML);
        }

        @Override
        public Optional<String> getLicenseCode()
        {
            return this.cloudArticleResolver.get()
                                            .flatMap(CloudArticle::getLicenseCode);
        }

        @Override
        public boolean isRetracted()
        {
            return this.cloudArticleResolver.get()
                                            .map(CloudArticle::isRetracted)
                                            .orElse(false);
        }

        @Override
        public Article resolvePDFIfPresent(Consumer<byte[]> pdfConsumer)
        {
            Optional.ofNullable(pdfConsumer)
                    .filter(consumer -> this.hasPDF())
                    .map(consumer -> BiElement.of(consumer, this.resolvePDF()))
                    .filter(bi -> bi.hasNoNullValue())
                    .filter(bi -> bi.getSecond()
                                    .isPresent())
                    .ifPresent(bi -> bi.getFirst()
                                       .accept(bi.getSecond()
                                                 .get()));
            return this;
        }

        @Override
        public String getId()
        {
            return this.id;
        }

        @Override
        public boolean hasPDF()
        {
            return this.cloudArticleResolver.get()
                                            .map(CloudArticle::hasPDF)
                                            .orElse(false);
        }

        @Override
        public boolean hasPMCReference()
        {
            return this.getPMCReference()
                       .isPresent();
        }

        @Override
        public Optional<PMCReference> getPMCReference()
        {
            return ArticleImpl.this.articleResolver.get()
                                                   .map(ArticleResult::getArticleids)
                                                   .map(List<ArticleId>::stream)
                                                   .orElse(Stream.empty())
                                                   .filter(articleId -> articleId.isPMC())
                                                   .findFirst()
                                                   .map(articleId -> articleId.getValue())
                                                   .map(pmcId -> new PMCReference() {

                                                       @Override
                                                       public String getLink(LinkType linkType)
                                                       {
                                                           String link = "https://www.ncbi.nlm.nih.gov/pmc/articles/" + this.getId();
                                                           return linkType.apply(link);
                                                       }

                                                       @Override
                                                       public String getId()
                                                       {
                                                           return pmcId;
                                                       }
                                                   });
        }

    }
}
