package org.omnaest.library.pmc;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.omnaest.library.pmc.ftp.PMCFtpUtils;
import org.omnaest.library.pmc.ftp.PMCFtpUtils.OpenAccessArticleIndex;
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
import org.omnaest.utils.exception.ExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PMCUtils implements Cacheable<PMCUtils>
{
    private static Logger LOG = LoggerFactory.getLogger(PMCUtils.class);

    private Cache            cache            = CacheUtils.newConcurrentInMemoryCache();
    private ExceptionHandler exceptionHandler = e -> LOG.error("Unexpected exception", e);

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

    public static interface Article
    {
        public String getTitle();

        public List<String> getAuthors();

        public Optional<LocalDate> getPublicationDate();

        public Optional<byte[]> resolvePDF();

        public Article resolvePDFIfPresent(Consumer<byte[]> pdfConsumer);

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
                                               .withCache(this.cache);
        Supplier<List<String>> supplier = new Supplier<List<String>>()
        {
            private int page = 0;

            @Override
            public List<String> get()
            {
                return accessor.searchFor(query, this.page++)
                               .getEsearchresult()
                               .getIdlist();
            }
        };
        CachedElement<OpenAccessArticleIndex> articleIndexSupplier = CachedElement.of(() -> this.loadOpenAccessArticleIndex());
        return StreamUtils.fromSupplier(supplier, List::isEmpty)
                          .flatMap(ids -> ids.stream()
                                             .map(id -> new ArticleImpl(accessor, articleIndexSupplier, id, this.cache, this.exceptionHandler)));
    }

    protected static class ArticleImpl implements Article
    {
        private final CachedElement<OpenAccessArticleIndex> articleIndexSupplier;
        private final String                                id;
        private final CachedElement<ArticleResult>          articleResolver;
        private final Cache                                 cache;
        private final ExceptionHandler                      exceptionHandler;

        private ArticleImpl(PMCRestAccessor accessor, CachedElement<OpenAccessArticleIndex> articleIndexSupplier, String id, Cache cache,
                            ExceptionHandler exceptionHandler)
        {
            this.articleIndexSupplier = articleIndexSupplier;
            this.id = id;
            this.cache = cache;
            this.exceptionHandler = exceptionHandler;
            this.articleResolver = CachedElement.of(() -> this.resolveArticle(accessor, id));
        }

        private ArticleResult resolveArticle(PMCRestAccessor accessor, String id)
        {
            return this.cache.computeIfAbsent("Article" + id, () -> Optional.ofNullable(accessor.getByArticleId(id))
                                                                            .map(article -> article.getResult())
                                                                            .map(result -> result.get(id))
                                                                            .orElse(null),
                                              ArticleResult.class);
        }

        @Override
        public String getTitle()
        {
            return this.articleResolver.get()
                                       .getTitle();
        }

        @Override
        public List<String> getAuthors()
        {
            return this.articleResolver.get()
                                       .getAuthors()
                                       .stream()
                                       .map(author -> author.getName())
                                       .collect(Collectors.toList());
        }

        @Override
        public Optional<LocalDate> getPublicationDate()
        {
            return parseDate(this.articleResolver.get()
                                                 .getPubdate());
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
            try
            {
                return Optional.ofNullable(this.cache.computeIfAbsent("PDF" + this.id, () -> this.articleIndexSupplier.get()
                                                                                                                      .resolvePDF(this.id),
                                                                      byte[].class));
            }
            catch (Exception e)
            {
                this.exceptionHandler.accept(e);
                return Optional.empty();
            }
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
            return this.articleIndexSupplier.get()
                                            .contains(this.id);
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
            return Optional.ofNullable(ArticleImpl.this.articleResolver.get())
                           .map(ArticleResult::getArticleids)
                           .map(List<ArticleId>::stream)
                           .orElse(Stream.empty())
                           .filter(articleId -> articleId.isPMC())
                           .findFirst()
                           .map(articleId -> articleId.getValue())
                           .map(pmcId -> new PMCReference()
                           {

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

    private OpenAccessArticleIndex loadOpenAccessArticleIndex()
    {
        return PMCFtpUtils.newInstance()
                          .usingCache(this.cache)
                          .loadOpenAccessArticleIndex();
    }
}
