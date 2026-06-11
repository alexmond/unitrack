# Project profile — unitrack

- profiled: 2026-06-11 by dc-scout
- languages: Java 21 (Spring Boot 4)
- build/package: Maven — `./mvnw verify` (full reactor build + tests + quality gates); `./mvnw -Pdocker -pl unitrack-web -am package` (OCI image via Cloud Native Buildpacks)
- frameworks/libs: Spring Boot 4.0.6, Spring Data JPA, Thymeleaf, Flyway, Lombok, Spring AI (MCP server), Bootstrap 5.3.8, Chart.js 4.4.1, WebJars (no CDN), Spring Security, Spring Mail
- test stack: JUnit 5 (via spring-boot-starter-test) — `./mvnw test` (runs all tests); `./mvnw -Dtest=ClassName test` (one class); core tests run on embedded H2 in PostgreSQL mode
- quality gate: spring-javaformat (code style, applied in `validate` phase), Checkstyle (SpringChecks + file/method length limits: 800 lines/80 lines), PMD (custom ruleset), JaCoCo (80% line coverage minimum on unitrack-core). Run all: `./mvnw verify` (quality gates are part of the reactor build)
- CI: GitHub Actions — `.github/workflows/ci.yml` (triggers on push to main and PRs; runs `./mvnw -B verify` + reports to UniTrack if configured)
- deploy/runtime: Docker (OCI image via Cloud Native Buildpacks in `-Pdocker` profile); Postgres (default, via Spring Data JPA + Flyway) or H2 (demo/embedded); scripts: `scripts/deploy-remote.sh` builds image on remote Docker host over SSH and deploys compose stack
- domain: Self-hosted server for tracking and reporting JUnit test execution and JaCoCo code coverage over time (think Allure Report meets Codecov); REST API + Thymeleaf dashboard + MCP server for AI assistants
- conventions (from memory): Use `@ConfigurationProperties` object binding (never `@Value`); serve frontend libs (Bootstrap, Chart.js) via WebJars in-jar (never CDN); use Maven properties for every dep/plugin version (no inline literals); Lombok for boilerplate (`@Getter`, `@Setter`, `@Slf4j`, `@RequiredArgsConstructor`); Spring Boot 4 gotcha: Flyway needs explicit `spring-boot-flyway` module, Jackson 3 is default (no `com.fasterxml` bean)

## Recommendations (drives the roster checkpoint)

- per-category lineups:
  - feature → architect(opus) → dev(sonnet) → qa(sonnet) → deployer(haiku)
  - bugfix → dev(sonnet) → qa(sonnet)
  - quality/refactor → dev(sonnet) → simplify(sonnet)
  
- dev tier for this repo: **sonnet** — mature multi-module Maven/Spring Boot project, heavy use of Spring abstractions (JPA, security, config binding), significant test coverage requirements (80% gate enforced), quality-gate enforcement in validate phase. Opus for architecture-level decisions; sonnet for standard feature work and troubleshooting.

- pre-arm candidate roles: 
  - **migration-specialist** — Flyway migrations present (schema changes go in unitrack-web/src/main/resources/db/migration/)
  - **security-specialist** — Spring Security + API tokens + GitHub commit-status integration + MCP server SSE auth
  - **reviewer** — heavy convention enforcement (spring-javaformat, Checkstyle, PMD, JaCoCo 80% gate), multi-module coordination required

- qa quality-gate command: `./mvnw verify` (full build including Checkstyle, PMD, format validation, JaCoCo 80% coverage gate on unitrack-core; all tests run against H2 in PostgreSQL mode)

- deployer CI target: GitHub Actions (`.github/workflows/ci.yml` is the canonical pipeline); for local/remote deploys, use `scripts/deploy-remote.sh --host <user@host> --stack <h2|postgres> --port <port>` (builds image on remote Docker daemon, no registry needed)
