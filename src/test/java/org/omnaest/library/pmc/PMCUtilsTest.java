package org.omnaest.library.pmc;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.omnaest.library.pmc.PMCUtils.Article;
import org.omnaest.library.pmc.PMCUtils.ArticleImpl;

public class PMCUtilsTest
{

    @Test
    @Ignore
    public void testSearchFor() throws Exception
    {
        Stream<Article> articles = PMCUtils.newInstance()
                                           .withLocalCache()
                                           .searchFor("covid"); //"adult polyglucosan body disease"
        articles
                //        .limit(20)
                .forEach(article ->
                {
                    String title = article.getTitle();
                    String id = article.getId();
                    System.out.println(title);

                    if (article.hasPDF())
                    {
                        try
                        {
                            byte[] pdf = article.resolvePDF();
                            if (pdf != null)
                            {
                                FileUtils.writeByteArrayToFile(new File("C:/Temp/pmc_pdfs_covid/" + id + ".pdf"), pdf);
                            }
                        }
                        catch (IOException e)
                        {
                            throw new IllegalStateException(e);
                        }
                    }
                });
    }

    @Test
    public void testParseDate() throws Exception
    {
        assertEquals("2020-03-09", ArticleImpl.parseDate("2020 Mar 9")
                                              .get()
                                              .format(DateTimeFormatter.ISO_DATE));
        assertEquals("2020-01-01", ArticleImpl.parseDate("2020 Jan 1")
                                              .get()
                                              .format(DateTimeFormatter.ISO_DATE));
        assertEquals("2020-12-31", ArticleImpl.parseDate("2020 Dec 31")
                                              .get()
                                              .format(DateTimeFormatter.ISO_DATE));

    }

}
