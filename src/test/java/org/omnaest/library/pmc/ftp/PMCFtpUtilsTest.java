package org.omnaest.library.pmc.ftp;

import static org.junit.Assert.assertNotNull;

import org.junit.Ignore;
import org.junit.Test;
import org.omnaest.library.pmc.ftp.PMCFtpUtils.OpenAccessArticleIndex;

public class PMCFtpUtilsTest
{

    @Test
    @Ignore
    public void testLoadOpenAccessArticleIndex() throws Exception
    {
        OpenAccessArticleIndex data = PMCFtpUtils.newInstance()
                                                 .loadOpenAccessArticleIndex();

        String url = data.findUrl("1389615");
        assertNotNull(url);
        System.out.println(url);
    }

}
