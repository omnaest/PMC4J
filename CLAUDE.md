# PMC4J

Client for PubMed Central (PMC) — searches and retrieves biomedical literature via the NCBI REST API and PMC FTP bulk downloads (`org.omnaest.library.pmc`). Entry point is `PMCUtils`.

## Build

```cmd
mvn clean install
mvn test -Dtest=MyTestClass#myMethod
```

## Architecture

Small library (11 files, 6 packages):

- **`PMCUtils`** — primary facade; article search and retrieval
- **`PMCRestUtils`** — NCBI E-utilities REST calls (ESearch, ESummary)
- **`PMCCloudUtils`** — article retrieval from the PMC Open Access AWS Open Data bucket
- **`PMCFtpUtils`** — **deprecated**; bulk download from the PMC FTP server
- **`rest/domain/raw/`**, **`cloud/domain/raw/`** — Jackson POJOs for the respective responses

### Bulk access: FTP is being switched off

NCBI restructured the PMC article datasets in 2026. `PMCFtpUtils` was repointed at the
`pub/pmc/deprecated/` subdirectory as a stopgap, but those legacy files are scheduled for
removal in **August 2026**, after which nothing in that class can succeed.

`PMCCloudUtils` is the replacement. The `pmc-oa-opendata` S3 bucket is world-readable over
plain anonymous HTTPS — no AWS SDK, no credentials. Each article version is one prefix,
`PMC<accession>.<version>/`, holding `.json` metadata, `.xml` (JATS), `.txt`, `.pdf` where one
exists, plus media. Because the key follows from the PMCID, resolving one article costs two
small requests instead of the 314 MB manifest download the FTP path needed.

`PMCUtils` still resolves PDFs via `PMCFtpUtils` — rewiring it onto `PMCCloudUtils` is the
outstanding piece of the migration.

## Code style

- No Lombok — hand-written
- Two access modes: REST (individual articles) and FTP (bulk)
- `ESearchResult` / `ArticleResult` are the primary result types from REST
- CSV parsing via `CommonsCSV` for FTP manifest files

## Package map

| Package | What lives here |
|---|---|
| `org.omnaest.library.pmc` | `PMCUtils` facade |
| `pmc.ftp` | `PMCFtpUtils` — FTP bulk access (deprecated) |
| `pmc.rest` | `PMCRestUtils` — ESearch/ESummary REST calls |
| `pmc.rest.domain.raw` | Jackson POJOs: `ESearchResult`, `ArticleResult`, `Article`, `Author`, `SearchResult` |
| `pmc.cloud` | `PMCCloudUtils` — AWS Open Data access |
| `pmc.cloud.domain.raw` | Jackson POJOs: `ArticleMetadata`, `ListBucketResult` |

## Dependencies (compile scope)

- `CommonsRESTClient` — NCBI REST API calls
- `CommonsCSV` — FTP manifest parsing
- `CommonsTable` — tabular result handling
- `CommonsFTP` — PMC FTP server access
- `CommonsLangAndIO` — IO utilities
- `jackson-dataformat-xml` — S3 `ListObjectsV2` responses; version managed by the jackson BOM in `CommonsParent`

Test scope: `CommonsTest`, `CommonsLog`.

Note: `RestClient.newXMLRestClient()` binds via **JAXB**, which rejects the namespaced S3
listing document. `PMCCloudUtils` therefore fetches that one response as a string and parses it
with Jackson's `XmlMapper`, which matches on local names.
