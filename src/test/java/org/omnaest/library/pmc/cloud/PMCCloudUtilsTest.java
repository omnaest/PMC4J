/*******************************************************************************
 * Copyright 2026 Danny Kunz
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
package org.omnaest.library.pmc.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.Ignore;
import org.junit.Test;
import org.omnaest.library.pmc.cloud.PMCCloudUtils.CloudArticle;

public class PMCCloudUtilsTest
{
    @Test
    public void testNormalizeId() throws Exception
    {
        assertEquals("PMC10000000", PMCCloudUtils.normalizeId("PMC10000000"));
        assertEquals("PMC10000000", PMCCloudUtils.normalizeId("10000000"));
        assertEquals("PMC10000000", PMCCloudUtils.normalizeId("pmc10000000"));
        assertEquals("PMC10000000", PMCCloudUtils.normalizeId("  PMC10000000 "));
    }

    /**
     * Hits the live open access bucket. Cheap - a version listing plus a small JSON document - but still a network test.
     */
    @Test
    @Ignore
    public void testFindArticle() throws Exception
    {
        CloudArticle article = PMCCloudUtils.newInstance()
                                            .findArticle("10000000")
                                            .get();

        assertEquals("PMC10000000", article.getId());
        assertEquals(1, article.getVersion());
        assertEquals("Editorial", article.getTitle());
        assertEquals(Optional.of("CC0"), article.getLicenseCode());
        assertTrue(article.isOpenAccess());
        assertFalse(article.isRetracted());
        assertTrue(article.hasPDF());
        assertFalse(article.getMediaLinks()
                           .isEmpty());
        assertEquals("https://pmc.ncbi.nlm.nih.gov/articles/PMC10000000/", article.getLink());
    }

    @Test
    @Ignore
    public void testResolvePDF() throws Exception
    {
        byte[] pdf = PMCCloudUtils.newInstance()
                                  .findArticle("PMC10000000")
                                  .flatMap(CloudArticle::resolvePDF)
                                  .get();

        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    /**
     * PMC11370360 carries more than one version, so it exercises the "latest wins" resolution.
     */
    @Test
    @Ignore
    public void testDetermineVersions() throws Exception
    {
        PMCCloudUtils cloudUtils = PMCCloudUtils.newInstance();

        assertEquals(List.of(1), cloudUtils.determineVersions("PMC10000000"));
        assertEquals(List.of(1, 2), cloudUtils.determineVersions("PMC11370360"));
        assertEquals(2, cloudUtils.findArticle("PMC11370360")
                                  .get()
                                  .getVersion());
    }

    /**
     * An accession id outside the open access subset must degrade to an empty result rather than throwing.
     */
    @Test
    @Ignore
    public void testFindArticleOfUnknownId() throws Exception
    {
        assertEquals(Optional.empty(), PMCCloudUtils.newInstance()
                                                    .findArticle("PMC999999999"));
    }

}
