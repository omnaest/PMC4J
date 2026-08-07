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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The per article version metadata document of the PMC Open Access AWS dataset, e.g.
 * <code>https://pmc-oa-opendata.s3.amazonaws.com/PMC10000000.1/PMC10000000.1.json</code>.
 * <p>
 * The <code>*_url</code> fields carry <code>s3://</code> URLs with the object md5 attached as a query parameter.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArticleMetadata
{
    @JsonProperty
    private String       pmcid;

    @JsonProperty
    private int          version;

    @JsonProperty
    private String       pmid;

    @JsonProperty
    private String       doi;

    @JsonProperty
    private String       mid;

    @JsonProperty
    private String       title;

    @JsonProperty
    private String       citation;

    @JsonProperty("is_pmc_openaccess")
    private boolean      openAccess;

    @JsonProperty("is_manuscript")
    private boolean      manuscript;

    @JsonProperty("is_historical_ocr")
    private boolean      historicalOcr;

    @JsonProperty("is_retracted")
    private boolean      retracted;

    @JsonProperty("license_code")
    private String       licenseCode;

    @JsonProperty("pdf_url")
    private String       pdfUrl;

    @JsonProperty("xml_url")
    private String       xmlUrl;

    @JsonProperty("text_url")
    private String       textUrl;

    @JsonProperty("media_urls")
    private List<String> mediaUrls;

    public String getPmcid()
    {
        return this.pmcid;
    }

    public void setPmcid(String pmcid)
    {
        this.pmcid = pmcid;
    }

    public int getVersion()
    {
        return this.version;
    }

    public void setVersion(int version)
    {
        this.version = version;
    }

    public String getPmid()
    {
        return this.pmid;
    }

    public void setPmid(String pmid)
    {
        this.pmid = pmid;
    }

    public String getDoi()
    {
        return this.doi;
    }

    public void setDoi(String doi)
    {
        this.doi = doi;
    }

    public String getMid()
    {
        return this.mid;
    }

    public void setMid(String mid)
    {
        this.mid = mid;
    }

    public String getTitle()
    {
        return this.title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getCitation()
    {
        return this.citation;
    }

    public void setCitation(String citation)
    {
        this.citation = citation;
    }

    public boolean isOpenAccess()
    {
        return this.openAccess;
    }

    public void setOpenAccess(boolean openAccess)
    {
        this.openAccess = openAccess;
    }

    public boolean isManuscript()
    {
        return this.manuscript;
    }

    public void setManuscript(boolean manuscript)
    {
        this.manuscript = manuscript;
    }

    public boolean isHistoricalOcr()
    {
        return this.historicalOcr;
    }

    public void setHistoricalOcr(boolean historicalOcr)
    {
        this.historicalOcr = historicalOcr;
    }

    public boolean isRetracted()
    {
        return this.retracted;
    }

    public void setRetracted(boolean retracted)
    {
        this.retracted = retracted;
    }

    public String getLicenseCode()
    {
        return this.licenseCode;
    }

    public void setLicenseCode(String licenseCode)
    {
        this.licenseCode = licenseCode;
    }

    public String getPdfUrl()
    {
        return this.pdfUrl;
    }

    public void setPdfUrl(String pdfUrl)
    {
        this.pdfUrl = pdfUrl;
    }

    public String getXmlUrl()
    {
        return this.xmlUrl;
    }

    public void setXmlUrl(String xmlUrl)
    {
        this.xmlUrl = xmlUrl;
    }

    public String getTextUrl()
    {
        return this.textUrl;
    }

    public void setTextUrl(String textUrl)
    {
        this.textUrl = textUrl;
    }

    public List<String> getMediaUrls()
    {
        return this.mediaUrls;
    }

    public void setMediaUrls(List<String> mediaUrls)
    {
        this.mediaUrls = mediaUrls;
    }

    @Override
    public String toString()
    {
        return "ArticleMetadata [pmcid=" + this.pmcid + ", version=" + this.version + ", pmid=" + this.pmid + ", doi=" + this.doi + ", title=" + this.title
               + ", citation=" + this.citation + ", openAccess=" + this.openAccess + ", retracted=" + this.retracted + ", licenseCode=" + this.licenseCode
               + ", pdfUrl=" + this.pdfUrl + "]";
    }

}
