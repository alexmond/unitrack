# Competitor screenshots

Reference screenshots of competitor tools, captured for [`../competitor-analysis.md`](../competitor-analysis.md).
Each is the vendor's own marketing/docs image; see the source URL. Use them only for internal
feature comparison.

> Codecov/Datadog/Trunk captured 2026-06-07 (vendor marketing/docs images). Allure Report
> added 2026-06-09 by headless-Chrome capture of the live demo (`demo.allurereport.org`).
> SonarQube and ReportPortal still serve their marketing images behind bot/hotlink protection —
> the rows under "Still to capture" need a manual or authenticated-demo grab.

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

| Want | Screen | Source page |
|---|---|---|
| SonarQube | My Account → Security (token create/expiry) | https://docs.sonarsource.com/sonarqube-server/user-guide/managing-tokens/ |
| SonarQube | Project dashboard / quality gate | https://www.sonarsource.com/products/sonarqube/ |
| ReportPortal | Profile → API keys; Edit personal info | https://reportportal.io/docs/user-account/EditPersonalInformation/ |
| ReportPortal | Configurable dashboard widgets | https://reportportal.io/docs/dashboards-and-widgets/ |
| Allure TestOps | Administration → Members (roles/RBAC) | https://docs.qameta.io/allure-testops/ |
| Coveralls | Coverage report / PR comment | https://coveralls.io/ |
