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

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.omnaest.library.pmc.rest.domain.raw.Article;
import org.omnaest.library.pmc.rest.domain.raw.SearchResult;
import org.omnaest.utils.cache.Cache;
import org.omnaest.utils.cache.Cacheable;
import org.omnaest.utils.rest.client.RestClient;

public class PMCRestUtils
{

    public static interface PMCRestAccessor extends Cacheable<PMCRestAccessor>
    {
        public PMCRestAccessor withBaseUrl(String baseUrl);

        public SearchResult searchFor(String query);

        public Article getByArticleId(String articleId);

        public SearchResult searchFor(String query, int page);

        public SearchResult searchFor(String query, int page, Sort sort);

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
        return new PMCRestAccessor()
        {
            private Cache  cache   = null;
            private String baseUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils";

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

                String url = RestClient.urlBuilder()
                                       .setBaseUrl(this.baseUrl)
                                       .addPathToken("esearch.fcgi")
                                       .addQueryParameter("db", "pmc")
                                       .addQueryParameter("term", query)
                                       .addQueryParameter("retmode", "json")
                                       .addQueryParameter("retmax", 20)
                                       .addQueryParameter("retstart", page * 20)
                                       .addQueryParameterIfPresent("sort", Optional.ofNullable(sort)
                                                                                   .map(Sort::get))
                                       .build();
                return restClient.requestGet(url, SearchResult.class);
            }

            @Override
            public Article getByArticleId(String articleId)
            {
                RestClient restClient = this.newRestClient();

                String url = RestClient.urlBuilder()
                                       .setBaseUrl(this.baseUrl)
                                       .addPathToken("esummary.fcgi")
                                       .addQueryParameter("db", "pmc")
                                       .addQueryParameter("id", articleId)
                                       .addQueryParameter("retmode", "json")
                                       .build();
                return restClient.requestGet(url, Article.class);
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

        };
    }
}
