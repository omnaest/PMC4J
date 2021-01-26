package org.omnaest.library.pmc.ftp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.io.IOUtils;
import org.omnaest.utils.CacheUtils;
import org.omnaest.utils.cache.Cache;
import org.omnaest.utils.csv.CSVUtils;
import org.omnaest.utils.ftp.FTPHelper;
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
            try
            {
                return IOUtils.toString(FTPHelper.loadFileContent("ftp://ftp.ncbi.nlm.nih.gov/pub/pmc/oa_non_comm_use_pdf.csv")
                                                 .get(),
                                        StandardCharsets.UTF_8);
            }
            catch (IOException e)
            {
                throw new IllegalStateException(e);
            }
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
                try
                {
                    return url == null ? null
                            : IOUtils.toByteArray(FTPHelper.loadFileContent(url)
                                                           .get());
                }
                catch (IOException e)
                {
                    throw new IllegalStateException(e);
                }

            }

            @Override
            public boolean contains(String id)
            {
                return this.findUrl(id) != null;
            }
        };
    }
}
