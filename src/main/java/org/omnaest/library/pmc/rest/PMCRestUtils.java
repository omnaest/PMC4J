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
package org.omnaest.library.pmc.rest;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.omnaest.library.pmc.rest.domain.raw.Article;
import org.omnaest.library.pmc.rest.domain.raw.ArticleResult;
import org.omnaest.library.pmc.rest.domain.raw.SearchResult;
import org.omnaest.utils.PredicateUtils;
import org.omnaest.utils.StreamUtils;
import org.omnaest.utils.cache.Cache;
import org.omnaest.utils.cache.Cacheable;
import org.omnaest.utils.rest.client.RestClient;
import org.omnaest.utils.rest.client.URLBuilder.URLBuilderWithBaseUrl;

public class PMCRestUtils
{
    /**
     * The number of article ids sent in a single ESummary request.
     * <p>
     * NCBI documents no hard maximum, but asks that requests carrying more than about 200 UIDs use HTTP POST. Measured against the live service a GET does in
     * fact survive roughly 350 PMC ids and then answers HTTP 414 (URI Too Long) from 400 onwards, so 200 both matches the documented guidance and keeps a
     * wide margin below the observed cliff - a margin that matters because PMC ids grow a digit every few years.
     */
    public static final int     MAX_ARTICLE_IDS_PER_REQUEST = 200;

    /**
     * Default value of the <code>tool</code> parameter, identifying this library to NCBI.
     */
    private static final String DEFAULT_TOOL                = "PMC4J";

    public static interface PMCRestAccessor extends Cacheable<PMCRestAccessor>
    {
        public PMCRestAccessor withBaseUrl(String baseUrl);

        /**
         * Sets the NCBI <code>api_key</code>, which raises the request limit from 3 to 10 requests per second per IP address. Keys are generated on the
         * settings page of an NCBI account; only one key is valid per account at a time.
         * <p>
         * Optional - without a key the accessor keeps working at the lower anonymous limit.
         *
         * @see <a href="https://www.ncbi.nlm.nih.gov/books/NBK25497/">E-utilities usage guidelines</a>
         */
        public PMCRestAccessor withApiKey(String apiKey);

        /**
         * Sets the <code>email</code> parameter, which must be a working address of the <b>developer</b> of the calling software rather than of an end user.
         * It buys no additional throughput; it is the address NCBI uses to warn about problematic traffic before blocking the offending IP, and registering
         * it becomes mandatory to get unblocked afterwards.
         */
        public PMCRestAccessor withContactEmail(String email);

        /**
         * Overrides the <code>tool</code> parameter, a space free name identifying the calling software to NCBI. Defaults to <code>PMC4J</code>.
         *
         * @see #withContactEmail(String)
         */
        public PMCRestAccessor withTool(String tool);

        public SearchResult searchFor(String query);

        public SearchResult searchFor(String query, int page);

        public SearchResult searchFor(String query, int page, Sort sort);

        public Article getByArticleId(String articleId);

        /**
         * Resolves multiple articles with a single ESummary request, which is what NCBI's rate limit makes worthwhile: one call for a whole result page rather
         * than one per article.
         * <p>
         * Ids beyond {@link PMCRestUtils#MAX_ARTICLE_IDS_PER_REQUEST} are split over as many requests as needed and merged back into one result, so callers
         * cannot accidentally build an over long URL.
         */
        public Article getByArticleIds(Collection<String> articleIds);

        public static enum Sort implements Supplier<String>
        {
            PUBLICATION_DATE("pub+date"), RELEVANCE("relevance"), FIRST_AUTHOR("first+author");
            private String queryParameterValue;

            private Sort(String queryParameterValue)
            {
                this.queryParameterValue = queryParameterValue;
            }

            @Override
            public String get()
            {
                return this.queryParameterValue;
            }
        }
    }

    public static PMCRestAccessor getInstance()
    {
        return new PMCRestAccessor() {
            private Cache  cache   = null;
            private String baseUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils";
            private String apiKey  = null;
            private String email   = null;
            private String tool    = DEFAULT_TOOL;

            @Override
            public SearchResult searchFor(String query)
            {
                int page = 0;
                return this.searchFor(query, page);
            }

            @Override
            public SearchResult searchFor(String query, int page)
            {
                Sort sort = null;
                return this.searchFor(query, page, sort);
            }

            @Override
            public SearchResult searchFor(String query, int page, Sort sort)
            {
                RestClient restClient = this.newRestClient();

                String url = this.addClientIdentification(RestClient.urlBuilder()
                                                                    .setBaseUrl(this.baseUrl)
                                                                    .addPathToken("esearch.fcgi")
                                                                    .addQueryParameter("db", "pmc")
                                                                    .addQueryParameter("term", query)
                                                                    .addQueryParameter("retmode", "json")
                                                                    .addQueryParameter("retmax", 20)
                                                                    .addQueryParameter("retstart", page * 20)
                                                                    .addQueryParameterIfPresent("sort", Optional.ofNullable(sort)
                                                                                                                .map(Sort::get)))
                                 .build();
                return restClient.requestGet(url, SearchResult.class);
            }

            @Override
            public Article getByArticleId(String articleId)
            {
                return this.getByArticleIds(Collections.singletonList(articleId));
            }

            @Override
            public Article getByArticleIds(Collection<String> articleIds)
            {
                Map<String, ArticleResult> result = StreamUtils.framedNonNullAsList(MAX_ARTICLE_IDS_PER_REQUEST, Optional.ofNullable(articleIds)
                                                                                                                        .orElse(Collections.emptyList())
                                                                                                                        .stream()
                                                                                                                        .filter(PredicateUtils.notNull()))
                                                               .map(this::requestArticleBatch)
                                                               .filter(PredicateUtils.notNull())
                                                               .map(Article::getResult)
                                                               .filter(PredicateUtils.notNull())
                                                               .flatMap(map -> map.entrySet()
                                                                                  .stream())
                                                               .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (first, second) -> first,
                                                                                         LinkedHashMap::new));

                Article article = new Article();
                article.setResult(result);
                return article;
            }

            private Article requestArticleBatch(List<String> articleIds)
            {
                RestClient restClient = this.newRestClient();

                String url = this.addClientIdentification(RestClient.urlBuilder()
                                                                    .setBaseUrl(this.baseUrl)
                                                                    .addPathToken("esummary.fcgi")
                                                                    .addQueryParameter("db", "pmc")
                                                                    .addQueryParameter("id", articleIds.stream()
                                                                                                       .collect(Collectors.joining(",")))
                                                                    .addQueryParameter("retmode", "json"))
                                 .build();
                return restClient.requestGet(url, Article.class);
            }

            /**
             * Adds the parameters by which NCBI identifies and rate limits the caller. All three are optional as far as the service is concerned, so a caller
             * that sets none keeps the previous anonymous behaviour.
             */
            private URLBuilderWithBaseUrl addClientIdentification(URLBuilderWithBaseUrl urlBuilder)
            {
                return urlBuilder.addQueryParameterIfNotNull("tool", this.tool)
                                 .addQueryParameterIfNotNull("email", this.email)
                                 .addQueryParameterIfNotNull("api_key", this.apiKey);
            }

            private RestClient newRestClient()
            {
                return RestClient.newJSONRestClient()
                                 .withCache(this.cache)
                                 .withRetry(5, 12, TimeUnit.SECONDS);
            }

            @Override
            public PMCRestAccessor withCache(Cache cache)
            {
                this.cache = cache;
                return this;
            }

            @Override
            public PMCRestAccessor withBaseUrl(String baseUrl)
            {
                this.baseUrl = baseUrl;
                return this;
            }

            @Override
            public PMCRestAccessor withApiKey(String apiKey)
            {
                this.apiKey = apiKey;
                return this;
            }

            @Override
            public PMCRestAccessor withContactEmail(String email)
            {
                this.email = email;
                return this;
            }

            @Override
            public PMCRestAccessor withTool(String tool)
            {
                this.tool = tool;
                return this;
            }

        };
    }
}
