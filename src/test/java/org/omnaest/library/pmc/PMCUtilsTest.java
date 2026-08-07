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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
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
                            byte[] pdf = article.resolvePDF()
                                                .get();
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

    /**
     * Drives the whole facade against the live services: ESearch for the id list, ESummary for the bibliographic data, and the open access bucket for the
     * PDF, full text and license.
     */
    @Test
    @Ignore
    public void testSearchForAndResolveContent() throws Exception
    {
        List<Article> articles = PMCUtils.newInstance()
                                         .searchFor("adult polyglucosan body disease")
                                         .limit(20)
                                         .collect(Collectors.toList());

        assertFalse(articles.isEmpty());
        articles.forEach(article -> assertNotNull(article.getTitle()));

        Article articleWithPDF = articles.stream()
                                         .filter(Article::hasPDF)
                                         .findFirst()
                                         .get();

        assertEquals("%PDF", new String(articleWithPDF.resolvePDF()
                                                      .get(),
                                        0, 4));
        assertTrue(articleWithPDF.resolveFullText()
                                 .get()
                                 .length() > 0);
        assertTrue(articleWithPDF.resolveXML()
                                 .get()
                                 .contains("<article"));
        assertTrue(articleWithPDF.getLicenseCode()
                                 .isPresent());
        assertFalse(articleWithPDF.isRetracted());
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
