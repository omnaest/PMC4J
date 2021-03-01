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
import org.omnaest.utils.table.components.TableIndex;
import org.omnaest.utils.table.domain.Row;

public class PMCFtpUtils
{
    private static final String FTP_FTP_NCBI_NLM_NIH_GOV_PUB_PMC = "ftp://ftp.ncbi.nlm.nih.gov/pub/pmc/";

    private Cache cache = CacheUtils.newConcurrentInMemoryCache();

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
                           .fromUrl("ftp://ftp.ncbi.nlm.nih.gov/pub/pmc/oa_non_comm_use_pdf.csv")
                           .map(FTPResource::asString)
                           .orElseThrow(() -> new IllegalStateException("Unable to load article index from ftp"));
        }, String.class);

        List<Map<String, String>> rows = CSVUtils.deserializer(CSVFormat.DEFAULT.withFirstRecordAsHeader())
                                                 .apply(csv)
                                                 .collect(Collectors.toList());

        Table table = Table.newInstance()
                           .load()
                           .fromRows(rows);

        TableIndex index = table.as()
                                .index("Accession ID");

        return new OpenAccessArticleIndex()
        {
            @Override
            public String findUrl(String id)
            {
                Optional<Row> row = index.getRowByValue("PMC" + id);
                return row.map(r -> r.getCell("File"))
                          .filter(cell -> !cell.isBlank())
                          .map(cell -> FTP_FTP_NCBI_NLM_NIH_GOV_PUB_PMC + cell.getValue())
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
