package org.omnaest.library.pmc.rest;

import java.util.List;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;
import org.omnaest.library.pmc.rest.PMCRestUtils.PMCRestAccessor;
import org.omnaest.library.pmc.rest.domain.raw.ArticleResult;
import org.omnaest.library.pmc.rest.domain.raw.SearchResult;

public class PMCRestUtilsTest
{
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
