# Competitor screenshots

Reference screenshots of competitor tools, captured for [`../competitor-analysis.md`](../competitor-analysis.md).
Each is the vendor's own marketing/docs image; see the source URL. Use them only for internal
feature comparison.

> Captured 2026-06-07. Allure Report, SonarQube and ReportPortal serve their images behind
> bot/hotlink protection — those rows below need a manual screen-grab from the live UI; the
> source URL points at the page that shows the screen.

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
| Allure Report | Test-results dashboard, behaviors, history | https://allurereport.org/ (hero), https://demo.qameta.io |
| SonarQube | My Account → Security (token create/expiry) | https://docs.sonarsource.com/sonarqube-server/user-guide/managing-tokens/ |
| SonarQube | Project dashboard / quality gate | https://www.sonarsource.com/products/sonarqube/ |
| ReportPortal | Profile → API keys; Edit personal info | https://reportportal.io/docs/user-account/EditPersonalInformation/ |
| ReportPortal | Configurable dashboard widgets | https://reportportal.io/docs/dashboards-and-widgets/ |
| Allure TestOps | Administration → Members (roles/RBAC) | https://docs.qameta.io/allure-testops/ |
| Coveralls | Coverage report / PR comment | https://coveralls.io/ |
