# UniTrack — Competitor & Feature Analysis

**Purpose:** Inform the UniTrack feature roadmap. UniTrack is a self-hosted Spring Boot 4
server that ingests **JUnit/Surefire test results** and **JaCoCo code coverage**, stores them
keyed by project/branch/commit, and reports trends (an Allure-Report-meets-Codecov for the JVM).
This document surveys established tools, compares the features that matter to UniTrack, and
distills the strongest ideas worth adopting.

> **Note on screenshots:** Live screenshots cannot be captured in this environment. Each tool
> section links to the official product/demo/docs pages where current screenshots live, labeled
> `[screenshots]`. Drop captured PNGs into `doc/images/` and replace the links as the project matures.
> Facts below were checked against vendor docs/sites in **June 2026**; pricing and features change —
> reconfirm before any decision. See **Sources** at the end.

---

## Feature comparison matrix

Legend: ✅ first-class · ⚠️ partial / add-on / awkward · ❌ not offered · — N/A

| Tool | JUnit ingest | Coverage ingest | Trends / history | Flaky detection | GitHub PR/commit | Dashboard UI | Self-host | Model |
|---|---|---|---|---|---|---|---|---|
| **Allure Report** (OSS) | ✅ many formats | ❌ | ⚠️ weak (history dir) | ⚠️ retries/“flaky” flag | ⚠️ via CI only | ✅ static HTML | ✅ (you host HTML) | Apache-2.0, free |
| **Allure TestOps** | ✅ | ❌ | ✅ historical trends | ✅ | ✅ | ✅ rich, configurable | ✅ (commercial) | Commercial SaaS/on-prem |
| **ReportPortal** | ✅ via agents/JUnit | ❌ | ✅ real-time + widgets | ⚠️ ML failure clustering | ⚠️ Jira-first, GH via CI | ✅ real-time, ML | ✅ (Apache-2.0) | OSS + paid SaaS |
| **Codecov** | ✅ Test Analytics | ✅ core product | ✅ coverage trends/sunburst | ✅ | ✅ checks + PR comments | ✅ | ✅ (self-hosted tier) | SaaS + self-host (paid) |
| **Coveralls** | ⚠️ limited | ✅ | ✅ coverage over time | ⚠️ | ✅ PR status | ✅ | ❌ (SaaS) | SaaS |
| **SonarQube** | ⚠️ imports coverage, not a test report | ✅ (imports JaCoCo) | ✅ quality/coverage trends | ❌ | ✅ PR decoration | ✅ | ✅ (Community Build free) | OSS + paid editions / Cloud |
| **Datadog Test Optimization** | ✅ JUnit XML upload | ⚠️ (intelligent test runner) | ✅ | ✅ central flaky mgmt | ✅ GH Action | ✅ | ❌ (SaaS) | SaaS, usage-based |
| **Currents.dev** | ✅ (Playwright/JS-leaning) | ❌ | ✅ | ✅ + quarantine | ✅ | ✅ | ❌ (SaaS) | SaaS, $49/mo+ |
| **Trunk Flaky Tests** | ✅ any runner | ❌ | ✅ | ✅ + auto-quarantine | ✅ | ✅ | ⚠️ on-prem (private preview) | Free tier + paid |
| **Jenkins JUnit plugin** | ✅ | ⚠️ via coverage plugins | ⚠️ per-job graphs | ⚠️ Test Results Analyzer | ⚠️ | ⚠️ basic | ✅ (you run Jenkins) | OSS, free |
| **🎯 UniTrack (target)** | ✅ Surefire XML | ✅ JaCoCo XML | ✅ per project/branch/commit | ✅ (roadmap) | ✅ (roadmap: checks) | ✅ Thymeleaf | ✅ first-class | OSS, self-host |

---

## Per-tool notes

### Allure Report (open source)
Lightweight reporter that runs in CI, reads test results from many frameworks, and emits a
**static HTML report**. Apache-2.0, no fees, but **you host the HTML and historical data is
awkward** — it only keeps limited history via a copied `history/` directory. Great inspiration for
UniTrack's *test-detail* views (steps, attachments, categories) but it is not a server/database.
Differentiators: rich per-test detail, attachments (logs/screenshots/video), categories of defects.
Docs/[screenshots]: https://allurereport.org/ · https://github.com/allure-framework

### Allure TestOps (commercial)
Server product layered on the Allure ecosystem: **historical trend analysis**, CI/CD integrations,
test scheduling, manual + automated tests unified, traceability and dashboards. This is the closest
"what UniTrack could grow into" on the *test-management* axis, but it's commercial and heavier.
Docs/[screenshots]: https://qameta.io/ · https://qameta.io/blog/allure-report-vs-allure-testops/

### ReportPortal
Apache-2.0, self-hostable, **real-time** aggregation of results from many frameworks with
**ML-based auto-analysis** that clusters recurring failures and learns triage decisions.
Configurable dashboards/widgets, Jira integration, REST API. Strong model for UniTrack's
real-time ingest + widget dashboard ideas. Docs/[screenshots]: https://reportportal.io/ ·
https://reportportal.io/docs

### Codecov
The Codecov-half of UniTrack's inspiration. Core is **code coverage** with **GitHub Checks
(line-by-line in PRs)**, status checks that block under-covered PRs, coverage **sunburst/trends**,
**Flags** (split coverage by frontend/backend/monorepo), **Components** (YAML filters), **report
merging**, a **CLI uploader**, and now **Test Analytics with flaky-test detection**. SaaS plus a
self-hosted tier. Docs/[screenshots]: https://about.codecov.io/product/features/ ·
https://docs.codecov.com/

### Coveralls
Lightweight SaaS that **tracks coverage over time** and posts **PR status**; good at visualizing
coverage/CI trends. SaaS-only. Docs/[screenshots]: https://coveralls.io/

### SonarQube
Code-quality platform that **imports JaCoCo coverage** (it does not generate it) alongside bugs,
smells, security, and **PR decoration**. Self-hosted **Community Build is free**; Developer/
Enterprise/Data Center editions and SonarQube Cloud are paid (from ~$32/mo). Not a test reporter,
but its **quality-gate** concept (fail the build if metrics regress) is worth borrowing.
Docs/[screenshots]: https://www.sonarsource.com/ · https://docs.sonarsource.com/

### Datadog Test Optimization
SaaS observability for CI tests. **JUnit XML upload** via `datadog-ci junit upload` (and a GitHub
Action), auto-discovers `*junit*.xml`/`TEST-*.xml`, captures `<system-out>/<system-err>/<failure>`
as connected logs, and uses tags to distinguish configs. **Centralized Flaky Tests Management**
shows pipeline failures, CI time wasted, and failure rate, with triage/quarantine. Excellent model
for UniTrack's flaky-test roadmap. Docs/[screenshots]: https://docs.datadoghq.com/tests/ ·
https://docs.datadoghq.com/tests/flaky_management/

### Currents.dev
SaaS dashboard (Playwright/JS-leaning) with **flakiness reports**, **test orchestration** to cut CI
time, and **quarantine**. From $49/mo for 10K results. Docs/[screenshots]: https://currents.dev/ ·
https://docs.currents.dev/dashboard/tests/flaky-tests

### Trunk Flaky Tests
Detects, **quarantines**, and eliminates flaky tests across **any language / runner / CI**, with
GitHub integration and multi-CI support. Free tier ($0/committer) plus paid; on-prem in private
preview. Docs/[screenshots]: https://trunk.io/flaky-tests · https://docs.trunk.io/flaky-tests

### Jenkins JUnit plugin (+ Test Results Analyzer)
The baseline most JVM teams already have: parses Surefire/JUnit XML, shows pass/fail trend graphs
per job, and the Test Results Analyzer plugin adds cross-build history. UI is dated and tied to
Jenkins. UniTrack should be strictly better at history, cross-project rollups, and coverage.
Docs/[screenshots]: https://plugins.jenkins.io/junit/

---

## Best features to adopt for UniTrack (prioritized)

**P0 — table stakes / already in scope**
1. **Surefire/JUnit XML + JaCoCo XML ingestion keyed by project/branch/commit** — *Datadog, Codecov, Jenkins*. Auto-discover `TEST-*.xml`; capture `<failure>`/`<system-out>`/`<system-err>` as connected logs (Datadog pattern).
2. **CLI/curl uploader + GitHub Action** — *Codecov, Datadog*. Copy-paste CI integration is the #1 adoption driver.
3. **Coverage + pass-rate trend lines per project/branch** — *Codecov, Coveralls, SonarQube*.
4. **Rich test-detail view** (status, duration, stacktrace, stdout/stderr, attachments) — *Allure*.

**P1 — strong differentiators worth building next**
5. **Flaky-test detection** = same test, same commit, both pass and fail; central flaky dashboard with "CI time wasted" + failure-rate metrics and quarantine — *Datadog, Trunk, Currents*.
6. **GitHub PR Checks / commit status** posting coverage delta + test summary line-by-line — *Codecov, Coveralls*.
7. **Quality gate**: fail the check if coverage drops or new failures appear vs. base branch — *SonarQube, Codecov status checks*.
8. **Coverage Flags/Components** to split a monorepo or front/back coverage — *Codecov*.

**P2 — later / nice-to-have**
9. **Configurable dashboard widgets** and **real-time ingest** — *ReportPortal, Allure TestOps*.
10. **ML/heuristic failure clustering** to group recurring failures — *ReportPortal*.
11. **Report merging** (multiple uploads per run, e.g. sharded CI) — *Codecov*.
12. **Defect categories / triage rules** for failures — *Allure, ReportPortal*.

---

## Gaps & next feature set — accounts, auth, API tokens, config

The P0–P2 roadmap above is now **delivered**. Re-reading the field, the biggest remaining gap
between UniTrack and the established tools is **not** test/coverage features — it's everything around
**identity, access, and configuration**. UniTrack today has **no authentication** (the ingest and
read APIs are open), **no user accounts or profiles**, **no API tokens**, and **no settings UI**
(config is properties/env only). Every competitor treats these as table stakes.

### What the others offer (and where)

| Capability | Who / where | UniTrack today |
|---|---|---|
| **Authentication / login** | All. Local + SSO/LDAP/OIDC — Allure TestOps (LDAP, Azure OIDC), SonarQube, ReportPortal | ❌ none (open) |
| **User profile page** | ReportPortal **Profile** page; SonarQube **My Account** | ❌ none |
| **Edit personal info** (name, email, password, avatar) | ReportPortal *Edit personal information*; SonarQube My Account | ❌ none |
| **Personal API tokens / keys** | ReportPortal **Profile → API Keys** (named, multiple, revocable, `Bearer`); SonarQube **My Account → Security** (named, expiry, revoke, email reminder) | ❌ none |
| **Ingest/upload token** | Codecov repo **upload token** + org **global upload token**; token required for uploads | ❌ `POST /api/v1/ingest` is unauthenticated |
| **Roles / RBAC** | Allure TestOps global (Admin/User/Guest) + project (Owner/Write/Read); Administration → Members | ❌ none |
| **Settings / config screens** | Codecov repo **Config** + **Org Settings**; SonarQube admin/quality-gate settings | ⚠️ `unitrack.quality-gate.*` / `unitrack.github.*` exist only as properties |
| **Notifications** | SonarQube emails on token expiry; gate/alert emails | ❌ none |

### Proposed next epic — "Accounts, auth & settings"

1. **Authentication + local user accounts** (foundation): login/logout, `User` entity, hashed
   passwords, session/security config. Prereq for everything below. *(SonarQube/ReportPortal/Allure)*
2. **User profile page** — view name, email, role, joined date. *(ReportPortal Profile)*
3. **Edit profile** — change name/email/password (avatar optional). *(ReportPortal edit personal info)*
4. **Personal API tokens** — generate (named, optional expiry), list, revoke, shown once. Authn via
   `Authorization: Bearer <token>` / header. *(ReportPortal API Keys, SonarQube Security)*
5. **Secure the ingest + read APIs with tokens** — require a token for `POST /api/v1/ingest` (and
   optionally reads); CI uses a token like Codecov's upload token. Back-compat "open mode" flag.
6. **Settings / config UI** — surface quality-gate + GitHub config per project (today properties-only)
   in an editable settings page. *(Codecov Config, SonarQube settings)*
7. **Roles & project membership** (later) — admin/member/viewer; Members admin page. *(Allure TestOps)*
8. **Notifications** (later) — email on token expiry and gate failures. *(SonarQube)*

> **Config/profile screen references** (external docs — drop captured PNGs into `doc/images/`):
> SonarQube *My Account → Security* tokens · ReportPortal *Profile → API Keys* · Codecov repo *Config →
> General* upload token · Allure TestOps *Administration → Members* roles. Links in **Sources**.

---

## Screenshots

Reference UI captures of the competitors live in [`images/`](images/) with a per-screenshot
index ([`images/README.md`](images/README.md)) mapping each to the UniTrack feature it parallels
— Codecov (coverage, flags, components, report-merging, PR/GitHub checks, Slack), Datadog
(flaky management, policies, notifications), Trunk (quarantine, failure grouping, PR comment,
status history), and **Allure Report** (Overview, Suites, Graphs, Behaviors, Timeline, Categories
— captured from the live OSS demo). The index also lists the auth/config/profile screens still to
capture from SonarQube and ReportPortal (their image hosts block automated download).

---

## Sources

- Allure Report: https://allurereport.org/ · https://github.com/allure-framework
- Allure Report vs TestOps: https://qameta.io/blog/allure-report-vs-allure-testops/
- ReportPortal: https://reportportal.io/ · https://reportportal.io/docs
- ReportPortal vs Allure: https://medium.com/@sarah.thoma.456/reportportal-vs-allure-report-a-comprehensive-comparison-5eb0d153ce73
- Codecov features: https://about.codecov.io/product/features/ · https://docs.codecov.com/
- Coveralls: https://coveralls.io/
- SonarQube pricing/editions: https://www.sonarsource.com/plans-and-pricing/
- SonarQube vs SonarCloud: https://dev.to/rahulxsingh/sonarqube-vs-sonarcloud-self-hosted-vs-cloud-code-quality-2026-dkj
- Datadog Test Optimization: https://docs.datadoghq.com/tests/ · JUnit upload: https://docs.datadoghq.com/tests/setup/junit_xml/ · Flaky mgmt: https://docs.datadoghq.com/tests/flaky_management/ · GH Action: https://github.com/DataDog/junit-upload-github-action
- Currents.dev: https://currents.dev/ · https://currents.dev/pricing · https://docs.currents.dev/dashboard/tests/flaky-tests
- Trunk Flaky Tests: https://trunk.io/flaky-tests · https://trunk.io/pricing · https://docs.trunk.io/flaky-tests
- Jenkins JUnit plugin: https://plugins.jenkins.io/junit/
- SonarQube tokens (My Account → Security): https://docs.sonarsource.com/sonarqube-server/latest/user-guide/managing-tokens/
- ReportPortal API keys (Profile): https://reportportal.io/docs/log-data-in-reportportal/HowToGetAnAccessTokenInReportPortal/ · profile: https://reportportal.io/docs/user-account/EditPersonalInformation/
- Codecov tokens: https://docs.codecov.com/docs/codecov-tokens
- Allure TestOps roles & members: https://help.qameta.io/support/solutions/articles/101000500968-roles-and-access-and-rights · https://docs.qameta.io/allure-testops/briefly/managing-users/ · LDAP: https://docs.qameta.io/allure-testops/configuration/authentication/ldap/
