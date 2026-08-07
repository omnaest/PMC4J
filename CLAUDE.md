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

`PMCUtils` resolves content via `PMCCloudUtils`; nothing in the facade reaches the FTP path any
more. `PMCFtpUtils` is retained only for callers still using it directly, which is why
`CommonsFTP`, `CommonsCSV` and `CommonsTable` are still dependencies — they drop out when that
class is finally deleted.

Because the cloud dataset covers the whole open access subset, `Article.hasPDF()` now also sees
commercial-use articles; the old FTP manifest indexed the non-commercial subset only.

### Do not nest cache lookups

`PMCUtils` and the `RestClient` share the caller's `Cache`. `CachedRestClient.requestGet` already
does a `computeIfAbsent` keyed by request URL, so wrapping an accessor call in a second
`computeIfAbsent` on the same cache nests them — and a concurrent in-memory cache
(`ConcurrentHashMap`) then throws `IllegalStateException: Recursive update` whenever the inner key
lands in the bin the outer one is updating. That makes it intermittent and hash-dependent: it hid
for years behind `withLocalCache()` and small result sets. Cache at one level only.

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
