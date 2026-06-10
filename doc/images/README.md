# Competitor screenshots

Reference screenshots of competitor tools, captured for [`../competitor-analysis.md`](../competitor-analysis.md).
Each is the vendor's own marketing/docs image; see the source URL. Use them only for internal
feature comparison.

> Codecov/Datadog/Trunk captured 2026-06-07 (vendor marketing/docs images). Allure Report
> (2026-06-09), SonarQube Cloud / ReportPortal / Coveralls (2026-06-10, live demos / public
> projects via headless Chrome), and Allure TestOps + the SonarQube token page (2026-06-10,
> vendor docs — both live screens are login-gated). Every screen referenced in the analysis
> is now captured.

## Allure Report

Captured 2026-06-09 from the live OSS demo report at https://demo.allurereport.org/
(headless Chrome, Overview then per-view via the in-app nav). The reference UI UniTrack
is benchmarked against for the rich test-detail / dashboard story.

| File | Shows | UniTrack parallel | Source |
|---|---|---|---|
| `allure-report-hero.png` | Marketing composite (donut + trend + suites) | Project overview + trends | allurereport.org |
| `allure-report-overview.png` | Live Overview dashboard (pass donut, trend, suites, categories, executors) | Project overview page | demo.allurereport.org/# |
| `allure-report-suites.png` | Suites tree (per-suite pass/fail) | Recent runs + suites | demo.allurereport.org/#suites |
| `allure-report-graphs.png` | Status/severity/duration/retries/categories trend graphs | Trend charts, perf trends | demo.allurereport.org/#graph |
| `allure-report-behaviors.png` | Behaviors (BDD features/stories) + failure marks | _gap — BDD grouping_ | demo.allurereport.org/#behaviors |
| `allure-report-timeline.png` | Execution timeline | _gap — timeline view_ | demo.allurereport.org/#timeline |
| `allure-report-categories.png` | Defect categories (e.g. Product defects) | **Triage rules / failure clusters** | demo.allurereport.org/#categories |

## SonarQube

`sonarcloud-overview.png` captured 2026-06-10 from a public project on https://sonarcloud.io
(no login; headless Chrome). `sonarqube-tokens.png` from the SonarQube Server docs (the token
screen sits behind a personal login).

| File | Shows | UniTrack parallel | Source |
|---|---|---|---|
| `sonarcloud-overview.png` | Project health dashboard — Quality Gate **Passed**, coverage %, duplications, security rating | **Quality gate** + coverage page | sonarcloud.io/project/overview |
| `sonarqube-tokens.png` | My Account → Security: Generate Tokens (name/type/**expires in**) + revoke | **API tokens** (create/expiry/revoke) | docs.sonarsource.com (managing-tokens) |

## ReportPortal

Captured 2026-06-10 from the public demo (`demo.reportportal.io`, pre-filled `default` login;
headless Chrome over the DevTools Protocol). The closest analogue to UniTrack's feature set.

| File | Shows | UniTrack parallel | Source |
|---|---|---|---|
| `reportportal-dashboard-widgets.png` | **Configurable widget dashboard** — Add/Edit/Lock widgets, launch-stats bar/area, growth + failed-cases trend charts | **#21 configurable widgets + real-time** | demo.reportportal.io |
| `reportportal-launches.png` | All Launches — per-launch pass/fail + Product Bug / Auto Bug / System Issue defect donuts | Recent runs + triage/clusters | demo.reportportal.io |
| `reportportal-login.png` | Login screen (GitHub OAuth + local account) | _gap — auth/login_ | demo.reportportal.io |
| `reportportal-api-keys.png` | Profile → API Keys: profile header (name/email/change-password) + **Generate API Key** | **API tokens** + profile | demo.reportportal.io |

## Coveralls

Captured 2026-06-10 from a public repo (no login; headless Chrome).

| File | Shows | UniTrack parallel | Source |
|---|---|---|---|
| `coveralls-report.png` | Repo coverage report — % badge, build history, relevant-lines-covered trend, files | Coverage page + trends | coveralls.io/github/parroty/excoveralls |

## Allure TestOps

From the Allure TestOps docs (`docs.qameta.io`); the live demo (`demo.testops.cloud`) is behind
OAuth. The commercial, "rich configurable" sibling of Allure Report.

| File | Shows | UniTrack parallel | Source |
|---|---|---|---|
| `allure-testops-dashboard.png` | **Configurable widget dashboard** — Automation donut, automation-trend chart, longest-running tests, test-coverage widgets, "+ Widget" | **#21 configurable widgets** | docs.qameta.io/allure-testops |
| `allure-testops-project.png` | Project page + left-nav (Test cases, Launches, Defects, Jobs, Settings) | Project sections / subnav | docs.qameta.io/allure-testops |

## Codecov

| File | Shows | UniTrack parallel | Source |
|---|---|---|---|
| `codecov-coverage.jpg` | Patch / holistic coverage report | Coverage trends + per-file | about.codecov.io/product/features |
| `codecov-test-analytics.jpg` | Test failures + flaky detection | Flaky detection, failures view | about.codecov.io/product/features |
| `codecov-status-checks.png` | PR merge-blocking status | Quality gate | about.codecov.io/product/features |
| `codecov-github-checks.png` | Line-by-line PR coverage check | GitHub commit status | about.codecov.io/product/features |
| `codecov-source-coverage.png` | Line-level coverage overlay | Coverage by file | about.codecov.io/product/features |
| `codecov-flags.png` | Coverage by frontend/backend flag | **Coverage flags** | about.codecov.io/product/features |
| `codecov-components.png` | Component-filtered coverage | Coverage flags/components | about.codecov.io/product/features |
| `codecov-report-merging.png` | Merge multiple coverage reports | **Report merging (runKey)** | about.codecov.io/product/features |
| `codecov-slack.png` | Coverage notifications in Slack | _gap — notifications_ | about.codecov.io/product/features |

## Datadog Test Optimization

| File | Shows | UniTrack parallel | Source |
|---|---|---|---|
| `datadog-flaky-management.png` | Flaky-tests management dashboard | Flaky detection + quarantine | docs.datadoghq.com/tests/flaky_management |
| `datadog-flaky-policies.png` | Flaky-test policy configuration | _gap — auto-quarantine policy_ | docs.datadoghq.com/tests/flaky_management |
| `datadog-notifications.png` | Flaky-test notification settings | _gap — notifications_ | docs.datadoghq.com/tests/flaky_management |

## Trunk Flaky Tests

| File | Shows | UniTrack parallel | Source |
|---|---|---|---|
| `trunk-quarantine.png` | Quarantined flaky tests homepage | Flaky quarantine | trunk.io/flaky-tests |
| `trunk-group-failures.png` | AI grouping of related failures | **Failure clustering** | trunk.io/flaky-tests |
| `trunk-github-comment.png` | Flaky results PR comment | **GitHub PR comment** (#93) | trunk.io/flaky-tests |
| `trunk-status-history.png` | Per-test status/flake history | Per-test history + blame | trunk.io/flaky-tests |

## Still to capture (bot-protected sources)

These screens back the gap analysis (config/profile/auth) but their hosts block automated
download — grab them manually from the live UI / docs:

_All screens referenced in the competitor analysis are now captured._ Add new rows here if a
future analysis section needs a screen this index doesn't yet have.
