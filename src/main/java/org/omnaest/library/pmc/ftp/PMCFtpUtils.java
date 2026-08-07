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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.omnaest.utils.CacheUtils;
import org.omnaest.utils.cache.Cache;
import org.omnaest.utils.csv.CSVUtils;
import org.omnaest.utils.ftp.FTPUtils;
import org.omnaest.utils.ftp.FTPUtils.FTPResource;
import org.omnaest.utils.table.Table;
import org.omnaest.utils.table.components.TableColumnIndex;
import org.omnaest.utils.table.domain.Row;

/**
 * Bulk access to the PMC Open Access Subset over NCBI's FTP service.
 * <p>
 * <b>Deprecated - this whole access path is being switched off by NCBI.</b> The PMC article datasets were restructured in 2026: both the OA PDF manifest and
 * the <code>oa_pdf/</code> tree it indexes were moved into a <code>deprecated/</code> subdirectory, and the former root paths now answer with FTP 550. The
 * URLs below were repointed at that subdirectory as a stopgap, but <code>ftp.ncbi.nlm.nih.gov/pub/pmc/readme.txt</code> schedules the legacy files themselves
 * for removal in <b>August 2026</b>, after which nothing in this class can succeed.
 * <p>
 * The replacement is the AWS Open Data distribution, reachable anonymously over plain HTTPS and modelled by {@link PMCCloudUtils}. It is strictly better for
 * this use case: a PDF is addressed by a key derived from the PMCID, so resolving one article no longer requires downloading and parsing a 314 MB manifest.
 *
 * @see PMCCloudUtils
 * @see <a href="https://pmc.ncbi.nlm.nih.gov/tools/cloud/">PMC cloud service documentation</a>
 */
@Deprecated
public class PMCFtpUtils
{
    private static final String PMC_FTP_BASE_URL          = "ftp://ftp.ncbi.nlm.nih.gov/pub/pmc/deprecated/";
    private static final String OPEN_ACCESS_PDF_INDEX_URL = PMC_FTP_BASE_URL + "oa_non_comm_use_pdf.csv";

    private Cache               cache                     = CacheUtils.newConcurrentInMemoryCache();

    public static PMCFtpUtils newInstance()
    {
        return new PMCFtpUtils();
    }

    private PMCFtpUtils()
    {
        super();
    }

    public PMCFtpUtils usingCache(Cache cache)
    {
        this.cache = cache;
        return this;
    }

    public static interface OpenAccessArticleIndex
    {
        public String findUrl(String id);

        public byte[] resolvePDF(String id);

        public boolean contains(String id);
    }

    public OpenAccessArticleIndex loadOpenAccessArticleIndex()
    {
        String csv = this.cache.computeIfAbsent("openAccessArticleIndex", () ->
        {
            return FTPUtils.load()
                           .withAnonymousCredentials()
                           .withNumberOfRetries(2)
                           .fromUrl(OPEN_ACCESS_PDF_INDEX_URL)
                           .map(FTPResource::asString)
                           .orElseThrow(() -> new IllegalStateException("Unable to load article index from ftp"));
        }, String.class);

        List<Map<String, String>> rows = CSVUtils.deserializer(CSVFormat.DEFAULT.withFirstRecordAsHeader())
                                                 .apply(csv)
                                                 .collect(Collectors.toList());

        Table table = Table.newInstance()
                           .load()
                           .fromRows(rows);

        TableColumnIndex index = table.as()
                                      .indexOfColumn("Accession ID");

        return new OpenAccessArticleIndex() {
            @Override
            public String findUrl(String id)
            {
                Optional<Row> row = index.getRowByValue("PMC" + id);
                return row.map(r -> r.getCell("File"))
                          .filter(cell -> !cell.isBlank())
                          .map(cell -> PMC_FTP_BASE_URL + cell.getValue())
                          .orElse(null);
            }

            @Override
            public byte[] resolvePDF(String id)
            {
                String url = this.findUrl(id);

                return url == null ? null
                        : FTPUtils.load()
                                  .withAnonymousCredentials()
                                  .withNumberOfRetries(5)
                                  .fromUrl(url)
                                  .map(FTPResource::asByteArray)
                                  .orElseThrow(() -> new IllegalStateException("Failed to load ftp url: " + url));
            }

            @Override
            public boolean contains(String id)
            {
                return this.findUrl(id) != null;
            }
        };
    }
}
