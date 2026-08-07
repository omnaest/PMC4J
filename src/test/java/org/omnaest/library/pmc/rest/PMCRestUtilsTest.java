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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;
import org.omnaest.library.pmc.rest.PMCRestUtils.PMCRestAccessor;
import org.omnaest.library.pmc.rest.domain.raw.ArticleResult;
import org.omnaest.library.pmc.rest.domain.raw.SearchResult;

public class PMCRestUtilsTest
{
    /**
     * A whole result page resolved with one ESummary request rather than one per article.
     */
    @Test
    @Ignore
    public void testGetByArticleIds() throws Exception
    {
        PMCRestAccessor restAccessor = PMCRestUtils.getInstance();

        List<String> idlist = restAccessor.searchFor("covid")
                                          .getEsearchresult()
                                          .getIdlist();
        assertFalse(idlist.isEmpty());

        Map<String, ArticleResult> result = restAccessor.getByArticleIds(idlist)
                                                        .getResult();

        assertEquals(idlist.size(), result.size());
        idlist.forEach(id -> assertNotNull(result.get(id)
                                                 .getTitle()));
    }

    /**
     * More ids than {@link PMCRestUtils#MAX_ARTICLE_IDS_PER_REQUEST}, so the accessor has to split the call and merge the responses. Sending them as one GET
     * would answer HTTP 414.
     */
    @Test
    @Ignore
    public void testGetByArticleIdsBeyondBatchLimit() throws Exception
    {
        PMCRestAccessor restAccessor = PMCRestUtils.getInstance();

        List<String> idlist = new ArrayList<>();
        for (int page = 0; idlist.size() <= PMCRestUtils.MAX_ARTICLE_IDS_PER_REQUEST + 40; page++)
        {
            idlist.addAll(restAccessor.searchFor("covid", page)
                                      .getEsearchresult()
                                      .getIdlist());
        }

        Map<String, ArticleResult> result = restAccessor.getByArticleIds(idlist)
                                                        .getResult();

        assertTrue(idlist.size() > PMCRestUtils.MAX_ARTICLE_IDS_PER_REQUEST);
        assertEquals(idlist.size(), result.size());
        idlist.forEach(id -> assertNotNull(result.get(id)
                                                 .getTitle()));
    }

    @Test
    @Ignore
    public void testGetInstance() throws Exception
    {
        PMCRestAccessor restAccessor = PMCRestUtils.getInstance()
                                                   .withLocalCache();
        SearchResult result = restAccessor.searchFor("rs25683");

        List<String> idlist = result.getEsearchresult()
                                    .getIdlist();
        System.out.println(idlist);

        String title = restAccessor.getByArticleId(idlist.get(0))
                                   .getResult()
                                   .values()
                                   .iterator()
                                   .next()
                                   .getTitle();
        System.out.println(title);
    }

    @Test
    @Ignore
    public void testGetInstance2() throws Exception
    {
        PMCRestAccessor restAccessor = PMCRestUtils.getInstance()
                                                   .withLocalCache();
        for (int page = 0; page <= 10; page++)
        {
            SearchResult result = restAccessor.searchFor("adult polyglucosan body disease", page);

            if (result == null || result.getEsearchresult()
                                        .getIdlist()
                                        .isEmpty())
            {
                break;
            }

            List<String> idlist = result.getEsearchresult()
                                        .getIdlist();
            System.out.println(idlist);

            idlist.forEach(id ->
            {
                Map<String, ArticleResult> resultMap = restAccessor.getByArticleId(id)
                                                                   .getResult();
                resultMap.forEach((key, value) ->
                {
                    ArticleResult articleResult = value;
                    String title = articleResult.getTitle();
                    System.out.println(key + ":" + title);
                });
            });
        }
    }

}
