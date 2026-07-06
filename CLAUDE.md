# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project Overview

UniTrack is a self-hosted server for tracking **JUnit test execution** and **code coverage**
over time (Allure-meets-Codecov for the JVM). CI uploads Surefire/JUnit XML + coverage
(JaCoCo/Cobertura/LCOV/OpenCover) per project/branch/commit; UniTrack stores every run and
renders trends, failures, flaky tests, quality gates, and per-file coverage. Built with
**Spring Boot 4.0.6 / Java 21**, a multi-module Maven build (`org.alexmond:unitrack-parent`,
version `0.1.0-SNAPSHOT`). Data in **PostgreSQL** via Spring Data JPA, schema by **Flyway**.

## Build, Test & Verify

```bash
scripts/dev-verify.sh                 # format + whole-reactor verify — green here = green PR
scripts/dev-test.sh <selector>        # targeted -Dtest run (last arg = selector); e.g.
scripts/dev-test.sh 'AuthIntegrationTest,Status*'
./mvnw spring-javaformat:apply        # auto-format (run before committing)
```

- `dev-verify.sh` / `dev-test.sh` are allowlisted in `.claude/settings.json` (run without prompts).
- CI (`.github/workflows/ci.yml`) runs `./mvnw -B verify` on JDK 21 — same gates as `dev-verify`.
- Tests are **hermetic**: in-memory H2 (`MODE=PostgreSQL`), seeded admin (`unitrack.security.admin-password=testadmin`).

## Architecture (modules)

- **`unitrack-core`** — domain (`domain/`), ingest + parsers (`ingest/`), reporting & quality gate
  (`report/`), JPA repositories (`repository/`). No web concerns.
- **`unitrack-web`** — the runnable Spring Boot app: REST API (`web/api/`), Thymeleaf dashboard
  (`web/ui/` + `templates/`), accounts/API tokens (`web/account/`, `web/security/`), GitHub
  commit-status + repo import (`web/github/`), MCP server (`web/mcp/`), demo seeder (`web/demo/`),
  observability (`web/metrics/` MeterBinder gauges, `web/ops/` health + `/ops` dashboard; ingest is
  wrapped in one `unitrack.ingest` Observation → derived Timer + span). Audit trail in `web/account/`
  (`AuditService` + `audit_entry`); `/actuator/{health,info,metrics,prometheus}` exposed.
- **`unitrack-cli`** — dependency-light CLI uploader + quality-gate checker.

Config is env-var driven (`unitrack-web/src/main/resources/application.yml`): `UNITRACK_DB_*`,
`PORT`, `UNITRACK_GITHUB_*`, `UNITRACK_SECURITY_*`, `UNITRACK_NOTIFICATIONS_*`. Settings classes:
`SecurityProperties` (`unitrack.security.*`), `GitHubProperties` (`unitrack.github.*`).

## Code Style & Quality (all fail the build at `validate`)

- **spring-javaformat** 0.0.47 — tabs, Spring conventions. Run `:apply` before committing.
- **Checkstyle** 3.6.0 (+ Spring checks) — config `checkstyle.xml` / `checkstyle-suppressions.xml`.
- **PMD** 3.28.0 — config `pmd-ruleset.xml`.
- **JaCoCo** 0.8.14 — 80% line gate on `unitrack-web` (excludes `web/demo/**`, `UnitrackApplication`).
- **Lombok** `@RequiredArgsConstructor` for constructor injection; `@ConfigurationProperties` for config.
- **File size** — aim to keep source files **under ~500 lines**; split god-classes into focused
  services (e.g. a fat controller → per-page `*PageService` helpers). This is a guideline, not a
  gate — Checkstyle only hard-fails `FileLength` at **800** lines. `DashboardController` rides that
  ceiling, so new routes there need a trim or an extract; new work should target 500, not 800.

Recurring lint rules that bite: **SpringTernary** wants `(a != b) ? x : y` (parenthesized, prefer
`!=`); **InnerTypeLast** (nested types after methods); **UseUnderscoresInNumericLiterals**
(`86_400`); **AppendCharacterWithChar** (`sb.append('m')` not `"m"`).

## Gotchas (load-bearing)

- **Spring Boot 4 moved packages.** Actuator health is `org.springframework.boot.health.*`
  (e.g. `…health.actuate.endpoint.HealthEndpoint`, `…health.contributor.*`); autoconfigure is split
  per-module (Flyway, etc.). Verify imports against the jars, not Boot 3 memory.
- **Boot 4 micrometer/actuator split.** The metrics + Prometheus endpoint autoconfig lives in
  `spring-boot-micrometer-metrics` (pulled transitively by `spring-boot-starter-actuator`); the
  registry backend (`micrometer-registry-prometheus`) is the only thing you add. `src/test/resources/
  application.yml` **shadows** the main one, so actuator exposure (`management.endpoints.web.exposure
  .include`) must be mirrored there or tests see only the default single endpoint. `MockMvc` doesn't
  map actuator web endpoints — assert the registry directly (`PrometheusMeterRegistry.scrape()`) or
  use `webEnvironment=RANDOM_PORT` + a real HTTP client.
- **Disable Compose lifecycle in containers**: the app sets `spring.docker.compose.enabled=true`
  for local dev, so deployments must set `SPRING_DOCKER_COMPOSE_ENABLED=false`.
- **Flyway migrations are immutable + versioned** in `unitrack-web/src/main/resources/db/migration/`.
  Latest is `V21__project_settings_gitlab.sql`; **next is V22**. Never edit a shipped migration.
- **Open mode by default** (`unitrack.security.open-mode=true`): all endpoints public so CI/uploader
  keep working. `/profile`, `/api/v1/me`, `/import`, and project settings/members always need auth.
- **Image build fails under rootless Podman** (buildpacks lifecycle bind-mounts the docker socket) —
  see issue #211; the K8s deploy currently uses a hand-built JRE image as a workaround.

## Deployment

- **Docker Compose**: `deploy/compose.postgres.yaml` (prod-like) / `compose.h2.yaml` (demo);
  `scripts/deploy-remote.sh` builds on a remote Docker host over SSH.
- **Kubernetes**: Helm chart `deploy/helm/unitrack` (toggleable bundled Postgres; pass env-specific
  overrides via `--values`); `scripts/deploy-k8s.sh` builds + `helm upgrade --install` + verifies.
- **Image**: built by buildpacks via the `docker` Maven profile
  (`./mvnw -Pdocker -pl unitrack-web -am package -Ddocker.image.name=… -Ddocker.publish=true`).

## Release (Maven Central)

- **Only the libraries publish**: `unitrack-core`, `unitrack-cli`, `unitrack-maven-plugin` (+ parent
  pom). The app `unitrack-web` is **not** in the top-level `<modules>` — it lives in an
  `activeByDefault` `default` profile. So `-Prelease` deactivates `default` and drops web from the
  reactor (apps don't go to Central); the `docker` profile **re-adds** web (since activating any
  profile deactivates `default`). **Gotcha:** any new `-P` profile that needs the app must also list
  `<module>unitrack-web</module>`, or web vanishes from that reactor.
- **Cut a release** via the `Maven release` workflow (`workflow_dispatch`, inputs `releaseVersion` /
  `nextVersion`): `versions:set` → `verify` → tag `v<version>` → `deploy -Prelease` (GPG sign +
  `central-publishing-maven-plugin`, `autoPublish`/`waitUntil=published`) → bump to next SNAPSHOT.
  Publishing is **irreversible** — never trigger without explicit go-ahead. Tagging `v<version>`
  also fires `publish-cli-image.yml` (GHCR uploader image).
- Secrets (repo-level, set from the `infra` repo's `gh-release-secrets.sh`): `OSSRH_USERNAME`,
  `OSSRH_PASSWORD`, `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`, `CODECOV_TOKEN`.
- The CLI's **main** jar is the thin library; the runnable fat jar is the `exec` classifier
  (`<classifier>exec</classifier>` on spring-boot-maven-plugin) — don't publish the fat jar as the
  primary artifact.

## Profiling (jvmlens)

Optional JVM profiling via the sibling **jvmlens** (`~/IdeaProjects/jvmlens`; not a build dep) —
turns a JFR into a ~400-token, source-attributed summary. Jars: `jvmlens-cli/target/jvmlens.jar`
(CLI/`trend`) + `jvmlens-agent/target/jvmlens-agent.jar`, or pull github.com/alexmond/jvmlens
`latest`. Always scope with `-a org.alexmond.unitrack`.

- **Dev one-shot (hot paths / allocations).** Profile under *steady-state* load, not a single
  render (else you profile Spring startup). Cleanest: a throwaway `@SpringBootTest` that ingests a
  few runs then renders `controller.run`/`controller.project` in a ~25s loop (**not** `@Transactional`,
  so the async page sections see committed data), run with
  `-DargLine="-XX:StartFlightRecording=filename=run.jfr,dumponexit=true,settings=profile"`, then
  `java -jar jvmlens.jar analyze run.jfr -a org.alexmond.unitrack` (`-r cpu|memory|locks`;
  `analyze -b before after` to diff a fix; `--assert` for a CI gate). NB: under the H2 test DB the
  allocation is dominated by H2's SQL parser (`Token$IdentifierToken`) — test-only; the
  query-VOLUME signal (e.g. the per-branch N+1 in `BranchService.summarize`, issue #314) is real on
  Postgres too.
- **Long-running monitor (prod, k3s).** This is **infra** — route through a Forgejo ticket (infra
  #24), not a project session. In-process agent with `history=…,interval=300,db,web,micrometer` on a
  restart-surviving volume, then `jvmlens trend`.

## Docs

Antora component under `docs/` (AsciiDoc); per-feature pages in `docs/modules/ROOT/pages/`.
Update the relevant page when changing a user-facing feature.
