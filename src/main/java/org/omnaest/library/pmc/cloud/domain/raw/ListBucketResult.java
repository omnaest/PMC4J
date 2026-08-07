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
package org.omnaest.library.pmc.cloud.domain.raw;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * The S3 <code>ListObjectsV2</code> response. Only the parts needed to enumerate the article version folders of a PMCID are mapped.
 * <p>
 * Note that the top level <code>Prefix</code> element echoes the request and is deliberately <b>not</b> mapped, so that it cannot be confused with the
 * <code>CommonPrefixes/Prefix</code> elements that carry the actual results.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ListBucketResult
{
    @JacksonXmlProperty(localName = "CommonPrefixes")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<CommonPrefix> commonPrefixes = new ArrayList<>();

    @JacksonXmlProperty(localName = "IsTruncated")
    private boolean            truncated;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommonPrefix
    {
        @JacksonXmlProperty(localName = "Prefix")
        private String prefix;

        public String getPrefix()
        {
            return this.prefix;
        }

        public void setPrefix(String prefix)
        {
            this.prefix = prefix;
        }

        @Override
        public String toString()
        {
            return "CommonPrefix [prefix=" + this.prefix + "]";
        }

    }

    public List<CommonPrefix> getCommonPrefixes()
    {
        return this.commonPrefixes;
    }

    public void setCommonPrefixes(List<CommonPrefix> commonPrefixes)
    {
        this.commonPrefixes = commonPrefixes;
    }

    public boolean isTruncated()
    {
        return this.truncated;
    }

    public void setTruncated(boolean truncated)
    {
        this.truncated = truncated;
    }

    @Override
    public String toString()
    {
        return "ListBucketResult [commonPrefixes=" + this.commonPrefixes + ", truncated=" + this.truncated + "]";
    }

}
