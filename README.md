# UniTrack

A self-hosted server for tracking and reporting **JUnit test execution** and **JaCoCo code
coverage** over time — think Allure Report meets Codecov, for the JVM. CI uploads Surefire/JUnit
XML and JaCoCo XML after each build; UniTrack stores every run keyed by project/branch/commit and
renders trends, failures, and per-file coverage on a dashboard.

Built with **Spring Boot 4** and **Java 21**, as a multi-module Maven project (`org.alexmond`).

> See [`doc/competitor-analysis.md`](doc/competitor-analysis.md) for the feature comparison against
> Allure, Codecov, ReportPortal, SonarQube, Datadog Test Optimization, Trunk, and others, plus the
> prioritized roadmap of features worth adopting.

## Features

- **Ingestion** — `POST /api/v1/ingest` accepts multipart Surefire/JUnit XML and JaCoCo XML, with
  project/branch/commit/build metadata. Projects are auto-created on first upload. Multiple files
  (e.g. sharded CI) are merged into one run.
- **Storage** — PostgreSQL via Spring Data JPA, schema managed by Flyway.
- **Trends & history** — pass-rate and line-coverage trend charts per project; full run history.
- **Flaky-test detection** — flags tests that both passed and failed for the same commit, with
  failure-rate and flaky-commit metrics and a quarantine toggle.
- **Quality gate** — PASS/FAIL verdict per run: minimum coverage, coverage drop vs the baseline
  branch, and new test failures relative to the baseline (quarantined flaky tests are excluded).
- **GitHub commit status** — on ingest, posts the gate verdict + coverage delta as a commit status
  (`unitrack/quality-gate`), so it surfaces on the commit and any associated PR.
- **Coverage flags / components** — tag an upload with a `flag` (e.g. `frontend`/`backend`) to
  track coverage per area; the quality-gate baseline is scoped to the same flag.
- **Report merging** — pass a `runKey` (e.g. a CI build id) so parallel/sharded jobs accumulate
  test results and coverage into a single run instead of fragmenting into many.
- **Failure clustering** — groups recurring failures by a normalized signature (type, message, top
  stack frame; numbers/hex/UUIDs masked) so triage sees root causes, not a wall of failures.
- **Triage rules** — per-project rules that match a failure's text and assign a category
  (product defect / test defect / infrastructure / …); categories show on the run page.
- **Dashboard** — server-rendered Thymeleaf UI: projects → runs → run detail (failures with
  stacktraces, captured `system-out`/`system-err` and `[[ATTACHMENT|…]]` links, suite breakdown,
  coverage by file).
- **REST API** — JSON endpoints for projects, runs, and run detail.
- **CI integration** — a `curl`-based uploader script and ready-to-copy GitHub Actions workflows.

## Architecture

A multi-module Maven build (`org.alexmond:unitrack-parent`), structured like
[`jhelm`](https://github.com/alexmond/jhelm):

```
unitrack-parent (pom)
├── unitrack-core          domain + persistence + ingestion (a plain library, no web)
│   org.alexmond.unitrack
│   ├── domain             JPA entities (Project, TestRun, TestSuiteResult, TestCaseResult,
│   │                      CoverageReport, CoverageFileEntry)
│   ├── repository         Spring Data JPA repositories
│   ├── ingest             XML parsers (JUnit, JaCoCo) + IngestService (parse → persist)
│   └── report             ReportingService (read-side queries shared by API + UI)
└── unitrack-web           Spring Boot application (depends on unitrack-core)
    org.alexmond.unitrack
    ├── UnitrackApplication
    └── web
        ├── api            IngestController, QueryController, ApiResponses, ApiExceptionHandler
        └── ui             DashboardController (Thymeleaf) + templates/static + db/migration
```

## Running locally

Requires JDK 21+ and Docker (for Postgres). Spring Boot's Docker Compose support starts Postgres
automatically from `compose.yaml`. Run the web module (`-am` also builds `unitrack-core`):

```bash
./mvnw -pl unitrack-web -am spring-boot:run
```

Then open <http://localhost:8080>. Health: <http://localhost:8080/actuator/health>.

To point at an existing Postgres instead, set `UNITRACK_DB_URL`, `UNITRACK_DB_USER`,
`UNITRACK_DB_PASSWORD`.

## Uploading results

Using the bundled script:

```bash
UNITRACK_URL=http://localhost:8080 \
scripts/unitrack-upload.sh \
  --project myapp \
  --branch  main \
  --flag    backend \
  --commit  "$(git rev-parse HEAD)" \
  --junit   'target/surefire-reports/TEST-*.xml' \
  --jacoco  'target/site/jacoco/jacoco.xml'
```

Or directly with `curl`:

```bash
curl -X POST \
  -F project=myapp -F branch=main -F commit=$SHA \
  -F 'junit=@target/surefire-reports/TEST-MyTest.xml;type=text/xml' \
  -F 'jacoco=@target/site/jacoco/jacoco.xml;type=text/xml' \
  http://localhost:8080/api/v1/ingest
```

For GitHub Actions, copy [`.github/workflows/upload-results-example.yml`](.github/workflows/upload-results-example.yml)
into your project and set a `UNITRACK_URL` repository variable.

## REST API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/ingest` | Upload JUnit (+ optional JaCoCo) reports for a run |
| `GET`  | `/api/v1/projects` | List projects with run counts |
| `GET`  | `/api/v1/projects/{id}` | Single project |
| `GET`  | `/api/v1/projects/{id}/runs?limit=50` | Recent runs for a project |
| `GET`  | `/api/v1/projects/{id}/flags` | Latest coverage/status per coverage flag (component) |
| `GET`  | `/api/v1/projects/{id}/failure-clusters` | Recent failures grouped by normalized signature |
| `GET`/`POST` | `/api/v1/projects/{id}/triage-rules` | List / create triage rules |
| `DELETE` | `/api/v1/triage-rules/{ruleId}` | Delete a triage rule |
| `GET`  | `/api/v1/runs/{id}/triage` | Categorize a run's failures + per-category counts |
| `GET`  | `/api/v1/runs/{id}` | Run detail: totals, suites, failures, coverage |
| `GET`  | `/api/v1/projects/{id}/flaky` | Detected flaky tests with metrics + quarantine state |
| `POST` | `/api/v1/projects/{id}/flaky/status` | Set a test's state (`ACTIVE`/`QUARANTINED`/`RESOLVED`) |
| `GET`  | `/api/v1/runs/{id}/quality-gate` | Evaluate the quality gate for a run (PASS/FAIL + per-rule detail) |

### Quality gate configuration

Tune via `unitrack.quality-gate.*` (defaults shown):

```yaml
unitrack:
  quality-gate:
    base-branch: main          # run on this branch is the comparison baseline
    min-line-coverage:         # absolute floor (unset = disabled)
    max-coverage-drop-pct: 1.0 # max allowed drop vs baseline, in percentage points
    fail-on-new-failures: true # fail on failures not present in the baseline (excl. quarantined)
```

### GitHub commit status

When enabled, each ingest posts a commit status (the quality-gate verdict + coverage delta) to the
project's GitHub repo. Disabled by default; the project's `repoUrl` and the run's `commit` must be
set. Configure via `unitrack.github.*`:

```yaml
unitrack:
  github:
    enabled: true
    token: ${GITHUB_TOKEN}              # PAT or App token with repo:status scope
    api-url: https://api.github.com     # override for GitHub Enterprise
    server-base-url: https://unitrack.example   # used to build status target links
    context: unitrack/quality-gate
```

## Build & test

```bash
./mvnw verify                              # build all modules + run tests (H2 in PostgreSQL mode)
./mvnw -pl unitrack-web -am spring-boot:run  # run against Postgres via Docker Compose
```

The boot jar is produced at `unitrack-web/target/unitrack.jar`. Each module also emits a JaCoCo
report at `<module>/target/site/jacoco/jacoco.xml` — UniTrack can ingest its own coverage.

### Code quality

Mirroring [`jhelm`](https://github.com/alexmond/jhelm), the build enforces quality gates in the
`validate` phase and uses Lombok to cut boilerplate:

- **spring-javaformat** — Spring code style. Run `./mvnw spring-javaformat:apply` to auto-format.
- **Checkstyle** — `SpringChecks` plus file/method length limits (`checkstyle.xml`, `checkstyle-suppressions.xml`).
- **PMD** — `pmd-ruleset.xml`.
- **Lombok** — `@Getter`/`@Setter`/`@NoArgsConstructor` on entities, `@RequiredArgsConstructor`/`@Slf4j` on services.
- **JaCoCo** — XML + HTML coverage reports per module.

Releasing (signed artifacts to Maven Central) is wired behind the `release` profile
(`./mvnw -Prelease deploy`) and requires a GPG key and Central credentials; it is inert otherwise.

## Roadmap

See the [epic and issues](https://github.com/alexmond/unitrack/issues) and the
["Best features to adopt"](doc/competitor-analysis.md#best-features-to-adopt-for-unitrack-prioritized)
section of the competitor analysis. Highlights: flaky-test detection, GitHub PR checks with
coverage deltas, and quality gates.

## License

Apache-2.0 (intended).
