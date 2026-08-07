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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.omnaest.library.pmc.cloud.domain.raw.ArticleMetadata;
import org.omnaest.library.pmc.cloud.domain.raw.ListBucketResult;
import org.omnaest.library.pmc.cloud.domain.raw.ListBucketResult.CommonPrefix;
import org.omnaest.utils.CacheUtils;
import org.omnaest.utils.cache.Cache;
import org.omnaest.utils.cache.Cacheable;
import org.omnaest.utils.exception.handler.ExceptionHandler;
import org.omnaest.utils.rest.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

/**
 * Access to the PMC Open Access Subset through NCBI's AWS Open Data distribution, the successor of the FTP bulk service modelled by
 * {@link org.omnaest.library.pmc.ftp.PMCFtpUtils}.
 * <p>
 * The bucket <code>pmc-oa-opendata</code> is world readable, so no AWS credentials and no AWS SDK are needed - every object is a plain anonymous HTTPS GET.
 * Each article version occupies one prefix, <code>PMC&lt;accession&gt;.&lt;version&gt;/</code>, holding the metadata JSON, the JATS XML, the extracted plain
 * text, the PDF where one exists, plus any media files:
 *
 * <pre>
 * https://pmc-oa-opendata.s3.amazonaws.com/PMC10000000.1/PMC10000000.1.json
 * https://pmc-oa-opendata.s3.amazonaws.com/PMC10000000.1/PMC10000000.1.pdf
 * </pre>
 *
 * Because the key follows from the PMCID, resolving a single article costs two small requests instead of the 314 MB manifest download the FTP path required.
 * <p>
 * Usage:
 *
 * <pre>
 * PMCCloudUtils.newInstance()
 *              .findArticle("PMC10000000")
 *              .filter(CloudArticle::hasPDF)
 *              .flatMap(CloudArticle::resolvePDF)
 *              .ifPresent(pdf -&gt; ...);
 * </pre>
 *
 * @see <a href="https://pmc.ncbi.nlm.nih.gov/tools/pmcaws/">Accessing PMC Article Datasets Using Amazon Web Services</a>
 */
public class PMCCloudUtils implements Cacheable<PMCCloudUtils>
{
    private static final Logger    LOG                      = LoggerFactory.getLogger(PMCCloudUtils.class);

    private static final XmlMapper XML_MAPPER               = new XmlMapper();

    private static final String    PMC_OA_OPENDATA_BASE_URL = "https://pmc-oa-opendata.s3.amazonaws.com/";

    private Cache               cache                    = CacheUtils.newConcurrentInMemoryCache();
    private String              baseUrl                  = PMC_OA_OPENDATA_BASE_URL;
    private ExceptionHandler    exceptionHandler         = e -> LOG.error("Unexpected exception", e);

    private PMCCloudUtils()
    {
        super();
    }

    public static PMCCloudUtils newInstance()
    {
        return new PMCCloudUtils();
    }

    @Override
    public PMCCloudUtils withCache(Cache cache)
    {
        this.cache = cache;
        return this;
    }

    public PMCCloudUtils withExceptionHandler(ExceptionHandler exceptionHandler)
    {
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    /**
     * Overrides the bucket endpoint, e.g. to point at a mirror. A trailing slash is added if missing.
     */
    public PMCCloudUtils withBaseUrl(String baseUrl)
    {
        this.baseUrl = StringUtils.appendIfMissing(baseUrl, "/");
        return this;
    }

    /**
     * A single article version within the open access dataset. Every <code>resolve*</code> method performs its download lazily and routes a failure to the
     * configured {@link ExceptionHandler} rather than throwing, so a failing article does not abort a surrounding stream.
     */
    public static interface CloudArticle
    {
        /**
         * The normalized accession id, e.g. <code>PMC10000000</code>.
         */
        public String getId();

        public int getVersion();

        public String getTitle();

        public String getCitation();

        public Optional<String> getDoi();

        public Optional<String> getPmid();

        /**
         * The license as encoded by PMC, e.g. <code>CC0</code> or <code>CC BY-NC</code>. Callers redistributing content must honour it.
         */
        public Optional<String> getLicenseCode();

        public boolean isOpenAccess();

        public boolean isRetracted();

        public boolean hasPDF();

        public Optional<byte[]> resolvePDF();

        /**
         * The full text as NISO JATS XML.
         */
        public Optional<String> resolveXML();

        /**
         * The full text as plain text, extracted from the XML by PMC.
         */
        public Optional<String> resolveText();

        /**
         * HTTPS links to figures and supplementary material, empty when the article has none.
         */
        public List<String> getMediaLinks();

        /**
         * The human readable article page on the PMC website.
         */
        public String getLink();

        public ArticleMetadata getRawMetadata();
    }

    /**
     * Resolves the highest available version of the given article. Accepts an accession id with or without the <code>PMC</code> prefix, so both
     * <code>PMC10000000</code> and the bare <code>10000000</code> of an ESearch id list work.
     *
     * @return empty if the article is not part of the open access dataset, or if the lookup failed
     */
    public Optional<CloudArticle> findArticle(String pmcId)
    {
        return this.determineVersions(pmcId)
                   .stream()
                   .max(Integer::compare)
                   .flatMap(version -> this.findArticle(pmcId, version));
    }

    /**
     * @see #findArticle(String)
     */
    public Optional<CloudArticle> findArticle(String pmcId, int version)
    {
        String id = normalizeId(pmcId);
        String prefix = id + "." + version;
        return this.fetch(prefix + "/" + prefix + ".json", ArticleMetadata.class)
                   .map(metadata -> new CloudArticleImpl(id, version, metadata));
    }

    /**
     * Determines the available versions of an article in ascending order, empty when the article is not part of the dataset.
     */
    public List<Integer> determineVersions(String pmcId)
    {
        String id = normalizeId(pmcId);
        String url = RestClient.urlBuilder()
                               .setBaseUrl(this.baseUrl)
                               .addQueryParameter("list-type", "2")
                               .addQueryParameter("prefix", id + ".")
                               .addQueryParameter("delimiter", "/")
                               .build();
        try
        {
            String xml = this.cache.computeIfAbsent("PMCCloudVersions" + id, () -> RestClient.newStringRestClient()
                                                                                             .withRetry(3, 5, TimeUnit.SECONDS)
                                                                                             .requestGet(url, String.class),
                                                    String.class);
            return Optional.ofNullable(xml)
                           .map(PMCCloudUtils::parseListBucketResult)
                           .map(ListBucketResult::getCommonPrefixes)
                           .orElse(Collections.emptyList())
                           .stream()
                           .map(CommonPrefix::getPrefix)
                           .map(PMCCloudUtils::parseVersion)
                           .filter(version -> version > 0)
                           .sorted()
                           .collect(Collectors.toList());
        }
        catch (Exception e)
        {
            this.exceptionHandler.accept(e);
            return Collections.emptyList();
        }
    }

    /**
     * Reads the S3 <code>ListObjectsV2</code> response.
     * <p>
     * Deliberately not routed through {@link RestClient#newXMLRestClient()}: that client binds via JAXB, which rejects the namespaced S3 document. Jackson's
     * {@link XmlMapper} matches on local names and ignores the namespace, which is what is wanted here.
     */
    private static ListBucketResult parseListBucketResult(String xml)
    {
        try
        {
            return XML_MAPPER.readValue(xml, ListBucketResult.class);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Unable to parse S3 listing response", e);
        }
    }

    /**
     * Extracts the version from an S3 folder prefix like <code>PMC10000000.1/</code>, or 0 if it does not follow that shape.
     */
    private static int parseVersion(String prefix)
    {
        String version = StringUtils.substringAfterLast(StringUtils.removeEnd(StringUtils.trimToEmpty(prefix), "/"), ".");
        return StringUtils.isNumeric(version) && !version.isEmpty() ? Integer.parseInt(version) : 0;
    }

    /**
     * Normalizes an accession id to the <code>PMC&lt;digits&gt;</code> form used as the S3 key prefix.
     */
    public static String normalizeId(String pmcId)
    {
        String id = StringUtils.trimToEmpty(pmcId);
        return "PMC" + (StringUtils.startsWithIgnoreCase(id, "PMC") ? id.substring(3) : id);
    }

    private class CloudArticleImpl implements CloudArticle
    {
        private final String          id;
        private final int             version;
        private final ArticleMetadata metadata;

        private CloudArticleImpl(String id, int version, ArticleMetadata metadata)
        {
            this.id = id;
            this.version = version;
            this.metadata = metadata;
        }

        @Override
        public String getId()
        {
            return this.id;
        }

        @Override
        public int getVersion()
        {
            return this.version;
        }

        @Override
        public String getTitle()
        {
            return this.metadata.getTitle();
        }

        @Override
        public String getCitation()
        {
            return this.metadata.getCitation();
        }

        @Override
        public Optional<String> getDoi()
        {
            return Optional.ofNullable(this.metadata.getDoi());
        }

        @Override
        public Optional<String> getPmid()
        {
            return Optional.ofNullable(this.metadata.getPmid());
        }

        @Override
        public Optional<String> getLicenseCode()
        {
            return Optional.ofNullable(this.metadata.getLicenseCode());
        }

        @Override
        public boolean isOpenAccess()
        {
            return this.metadata.isOpenAccess();
        }

        @Override
        public boolean isRetracted()
        {
            return this.metadata.isRetracted();
        }

        @Override
        public boolean hasPDF()
        {
            return this.metadata.getPdfUrl() != null;
        }

        @Override
        public Optional<byte[]> resolvePDF()
        {
            return PMCCloudUtils.this.fetchBinary(this.metadata.getPdfUrl());
        }

        @Override
        public Optional<String> resolveXML()
        {
            return PMCCloudUtils.this.fetchText(this.metadata.getXmlUrl());
        }

        @Override
        public Optional<String> resolveText()
        {
            return PMCCloudUtils.this.fetchText(this.metadata.getTextUrl());
        }

        @Override
        public List<String> getMediaLinks()
        {
            return Optional.ofNullable(this.metadata.getMediaUrls())
                           .orElse(Collections.emptyList())
                           .stream()
                           .map(PMCCloudUtils.this::toObjectUrl)
                           .collect(Collectors.toList());
        }

        @Override
        public String getLink()
        {
            return "https://pmc.ncbi.nlm.nih.gov/articles/" + this.id + "/";
        }

        @Override
        public ArticleMetadata getRawMetadata()
        {
            return this.metadata;
        }

        @Override
        public String toString()
        {
            return "CloudArticle [id=" + this.id + ", version=" + this.version + ", title=" + this.getTitle() + "]";
        }

    }

    private Optional<byte[]> fetchBinary(String s3Url)
    {
        return this.resolveObject(s3Url, url -> this.cache.computeIfAbsent("PMCCloudBinary" + url, () -> RestClient.newByteArrayRestClient()
                                                                                                                  .requestGet(url, byte[].class),
                                                                           byte[].class));
    }

    private Optional<String> fetchText(String s3Url)
    {
        return this.resolveObject(s3Url, url -> this.cache.computeIfAbsent("PMCCloudText" + url, () -> RestClient.newStringRestClient()
                                                                                                                .requestGet(url, String.class),
                                                                           String.class));
    }

    private <T> Optional<T> resolveObject(String s3Url, java.util.function.Function<String, T> resolver)
    {
        try
        {
            return Optional.ofNullable(s3Url)
                           .map(this::toObjectUrl)
                           .map(resolver);
        }
        catch (Exception e)
        {
            this.exceptionHandler.accept(e);
            return Optional.empty();
        }
    }

    private <T> Optional<T> fetch(String key, Class<T> type)
    {
        try
        {
            return Optional.ofNullable(this.cache.computeIfAbsent("PMCCloud" + type.getSimpleName() + key, () -> this.newJSONRestClient()
                                                                                                                    .requestGet(this.baseUrl + key, type),
                                                                  type));
        }
        catch (Exception e)
        {
            this.exceptionHandler.accept(e);
            return Optional.empty();
        }
    }

    /**
     * Translates an <code>s3://bucket/key?md5=...</code> URL as found in the metadata document into an anonymous HTTPS URL against the configured endpoint.
     */
    private String toObjectUrl(String s3Url)
    {
        String keyWithMd5 = StringUtils.substringAfter(StringUtils.removeStart(s3Url, "s3://"), "/");
        return this.baseUrl + StringUtils.substringBefore(keyWithMd5, "?");
    }

    private RestClient newJSONRestClient()
    {
        return RestClient.newJSONRestClient()
                         .withRetry(3, 5, TimeUnit.SECONDS);
    }
}
