# UniTrack — UI data map (per-tab, pre-reconcile)

Exhaustive inventory of every screen and the data it shows, organized **per tab** with its
own drill path. This is the *document-everything* pass; **reconciliation across tabs is a
later step** (see the TODO at the end) — nothing here is unified yet.

Source of truth: `web/ui/` controllers + `resources/templates/`.

## Level scheme

Depth of navigation, not screen type:

- **L0 — Global**: all projects (board).
- **L1 — Project summary**: one project, all aspects rolled up (Overview).
- **L2 — Aspect summary**: one tab = one aspect, summarized (Tests, Coverage, Timing, Load, Flaky, Clusters).
- **L3 — In-aspect list / breakdown**: the lists & groupings inside a tab (by-module, all-tests, packages, slowest, recent perf runs…). Some are in-page sections; some link deeper.
- **L4 — Entity**: a single thing — a run, a perf-run, one test's history, a comparison, a PR.
- **L5 — Entity detail**: sub-sections of an entity (a run's failures/coverage-by-file; a perf-run's per-label rows; a test's timeline) and exits (export, share).

> A given screen can sit at different depths depending on the tab you came from — that
> overlap is exactly what the reconcile pass will resolve. For now each tab is documented
> standalone, and the deep shared entities (Run, Perf-run, Compare, Test history) are
> written once under **Shared entities** and referenced from each tab.

---

## L0 — Board (`/`, `index.html`)
- **Global stats:** project count · failing gates · flaky total · avg coverage %
- **Per-project health card** (`ProjectHealth`, failing-first): name · branch · status · pass % · coverage % · flaky count · last-run time · (red:) broken-since · days-red · runs-red
- **Drill →** L1 project Overview

## L1 — Project Overview (`/projects/{id}`, `project.html`)
The whole-project summary (mixes tests + coverage + branches + runs).
- **Filter:** branch dropdown (>1 branch)
- **Hero:** pass-rate donut (pass%/fail%) · status pill · passed/total · coverage % · latest-run link (commit·branch·time)
- **Trend (L1 chart):** passed/failed/skipped + line-cov %; time/run toggle; regressed-since overlay + gap band; point → **Compare** (L4)
- **L3 sections:**
  - Coverage by module → **Coverage tab** filtered (`?module`)
  - Coverage by flag: flag · line cov · latest · status
  - Branches: branch · status · pass% · cov · runs · last seen → branch-scoped Overview
  - Pull requests: PR · branch · status · cov · runs · latest → **PR** (L4)
  - Recent runs: when · branch · flag · commit · status · tests · failed · skipped · pass% · cov · duration → **Run** (L4)
  - Compare runs form → **Compare** (L4)

---

## L2 — Tabs (per aspect)

### Tests (`/projects/{id}/tests`, `tests.html`)
- **L2 summary:** KPI tiles (pass rate · failures · suite time · tests, each +Δ vs prev run) · latest-run line · **trend** (passed/failed per run; time/run toggle; regression overlay; point → Compare L4)
- **L3 — Tests by module:** module · tests · pass% · failures · skipped — *click filters the roster (in-page)*
- **L3 — All tests** (search box): Test · Status · Duration · Flaky badge → Flaky tab
  - **L4 — Single test history** (`/projects/{id}/test`) via the Test link → *see Shared entities*
- *Gap:* no flag scope (locked to default rollup flag)

### Coverage (`/projects/{id}/coverage`, `coverage.html`)
- **L2 summary:** KPI tiles (line +Δ · branch · instruction · method) · latest-report line → **Run** (L4) · **trend** (line-cov %; time/run toggle; point → Compare L4)
- **L3 — Coverage by module:** module · line% · lines · files — *click scopes the tables below (`?module`)*
- **L3 — By package:** package · line% · bar · branch% · lines  *(not linked deeper)*
- **L3 — Worst-covered files:** file · line% · bar · covered · missed  *(not linked deeper — no file-detail screen)*

### Test timing (`/projects/{id}/performance`, `performance.html`)
- **L2 summary:** KPI tiles (suite time +Δ · slowest test · runs tracked) · **trend** (suite-time; time/run toggle; point → Compare L4)
- **L3 — Timing by module:** module · tests · total time · avg — *click filters the slowest list (in-page)*
- **L3 — Slowest tests:** test · suite · duration · status
  - **L4 — Single test history** via the test link → *see Shared entities*
- *Gap:* no flag scope

### Load tests (`/projects/{id}/perf`, `perf.html`)
- **L2 summary:** perf-flag **pill bar** (filter) · p95-regression banner (onset commit/date · baseline→recent · σ) · KPI tiles (p95 · throughput · error rate · samples, +Δ)
- **L2 charts:** Latency (p50/p90/p99) · Throughput · Error rate (shared time/run toggle; point → **Perf-run** L4)
- **L3 — Recent perf runs:** when · flag · commit · format · samples · p95 · throughput · errors → **Perf-run** (L4)
- *Note:* groups by perf-**flag**, not module; only tab still on a top pill bar

### Flaky tests (`/projects/{id}/flaky`, `flaky.html`)
- **L2 list:** Test · State · Flaky commits · Failures · Fail rate · Last failure · Action (state mgmt) — *flat; no run drill*

### Failure clusters (`/projects/{id}/clusters`, `clusters.html`)
- **L2:** Clusters (grouped current failures) · Recurring failures · AI root-cause analyze — *flat; links back to project only*

### Management tabs (write access)
- Owners (Test owners · Scorecard · Rules · Add rule) · Triage rules · Alerts · Settings (gate/visibility/base branch) · Members · per-project Ingest history

---

## Shared entities (deep drill targets, reached from several tabs)

### Run detail (`/runs/{id}`, `run.html`) — L4
Reached from: Overview recent-runs, Coverage latest-report, single-test history rows, PR runs, Compare.
- **L4 header stats:** Tests · Passed · Failed · Skipped · Duration · Line cov
- **L5 sub-sections:**
  - Failures: test · baseline · now · Δ (regression vs baseline run → links baseline Run)
  - Suites: suite · tests · failures · errors · skipped · time
  - Slowest tests: test · duration
  - Coverage change: file · baseline · this run · Δ pp
  - Coverage by module: module · line% · lines · files
  - Coverage by file: file · line% · bar · covered · missed
- **L5 nav/exits:** prev/next run · baseline run · **Compare** · **Export** (`/runs/{id}/export`) · AI analyze · **Share** (`/share/{token}`)

### Perf-run detail (`/perf-runs/{id}`, `perf-run.html`) — L4
Reached from: Load-tests charts + recent perf runs.
- **L5:** per-label breakdown · regression vs baseline perf-run (links baseline) · prev/next perf-run

### Single test history (`/projects/{id}/test`, `test.html`) — L4
Reached from: Tests roster, Timing slowest.
- **L5:** Status timeline · Duration trend · History rows → **Run** (L4/L5)

### Compare runs (`/compare`, `compare.html`) — L4
Reached from: any trend point (Overview/Tests/Coverage/Timing), Overview compare form, Run.
- **L5:** Newly failing · Fixed · Still failing · Timing changes (test · base · head · Δ)

### Pull request (`/projects/{id}/pr/{pr}`, `pr.html`) — L4
Reached from: Overview PRs.
- Coverage change · Runs on this PR → **Run** (L4)

---

## Reconcile TODO (next pass — do NOT apply yet)
Once every tab is documented, align across tabs:
- Consistent **level depth** per aspect (some tabs are flat at L2: Flaky, Clusters; others go L2→L3→L4→L5).
- **Flag vs module** model: Load tests filters by flag (pills); others break down by module (list). Pick one navigation idiom.
- **Flag scope** missing on Tests & Test timing.
- **Dead ends:** coverage package/file rows and flaky/cluster rows don't drill to an entity — decide whether they should.
- **Shared entity reuse:** Run / Compare / Test-history are reached at differing depths per tab; define one canonical path.
