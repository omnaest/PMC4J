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
