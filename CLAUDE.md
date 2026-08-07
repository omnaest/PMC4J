# PMC4J

Client for PubMed Central (PMC) — searches and retrieves biomedical literature via the NCBI REST API and PMC FTP bulk downloads (`org.omnaest.library.pmc`). Entry point is `PMCUtils`.

## Build

```cmd
mvn clean install
mvn test -Dtest=MyTestClass#myMethod
```

## Architecture

Small library (8 files, 4 packages):

- **`PMCUtils`** — primary facade; article search and retrieval
- **`PMCRestUtils`** — NCBI E-utilities REST calls (ESearch, EFetch)
- **`PMCFtpUtils`** — bulk article download from PMC FTP server
- **`rest/domain/raw/`** — Jackson POJOs for ESearch and EFetch XML/JSON responses

## Code style

- No Lombok — hand-written
- Two access modes: REST (individual articles) and FTP (bulk)
- `ESearchResult` / `ArticleResult` are the primary result types from REST
- CSV parsing via `CommonsCSV` for FTP manifest files

## Package map

| Package | What lives here |
|---|---|
| `org.omnaest.library.pmc` | `PMCUtils` facade |
| `pmc.ftp` | `PMCFtpUtils` — FTP bulk access |
| `pmc.rest` | `PMCRestUtils` — ESearch/EFetch REST calls |
| `pmc.rest.domain.raw` | Jackson POJOs: `ESearchResult`, `ArticleResult`, `Article`, `Author`, `SearchResult` |

## Dependencies (compile scope)

- `CommonsRESTClient` — NCBI REST API calls
- `CommonsCSV` — FTP manifest parsing
- `CommonsTable` — tabular result handling
- `CommonsFTP` — PMC FTP server access
- `CommonsLangAndIO` — IO utilities

Test scope: `CommonsTest`, `CommonsLog`.
