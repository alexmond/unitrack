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

### Tests (`/projects/{id}/tests`, `tests.html`) — reconciled; Flaky + Clusters folded in
- **L2 summary:** KPI tiles (pass rate · failures · suite time · tests, each +Δ vs prev run) · latest-run line → **Run** (L4) · **trend** (passed/failed per run; time/run toggle; regression overlay; point → Compare L4)
- **L3 — Tests by module:** module · tests · pass% · failures · skipped — *click scopes the whole tab (tiles + trend + roster) via `?module=`; chip + "← All tests" returns (D7)*
- **L3 — All tests** (search-first; sortable): default = failing + flaky (counts strip) with "Show all N" for the rest; Test · Status (+ green **fixed** = red→green) · Duration · Flaky badge (→ folded Flaky section) (D8)
  - **L4 — Single test history** (`/projects/{id}/test`) via the Test link → *see Shared entities*
- **L3 — Flaky tests** (folded section): Test · State · Flaky commits · Failures · Fail rate · Last failure · Action (quarantine, writers only)
- **L3 — Failure clusters** (folded section): clusters (grouped current failures) · recurring failures · AI root-cause analyze (logged-in)
- *Gap:* no flag scope yet (locked to default rollup flag) — #405

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

### Flaky tests — **folded into Tests** (`/projects/{id}/flaky` → 302 `…/tests#flaky-section`)
### Failure clusters — **folded into Tests** (`/projects/{id}/clusters` → 302 `…/tests#clusters-section`)
> Both are now sections of the reconciled Tests page (epic #390); the standalone tabs and
> `flaky.html`/`clusters.html` were removed. The `/new-tests` preview graduated to `/tests`.

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

## Reconciliation spec (panel output — for review)

Cross-tab reconciliation from a 7-role design panel (UX/IA director, frontend/design-system,
competitor analyst, QA, restraint skeptic, developer advocate, performance engineer). **Design
only — no code.** Goal/bar: *a user who learns one tab can predict every other tab — same
levels, same idioms, no dead ends, one path to each shared entity.* The Overview page is the
model the others converge on.

### Core decisions (resolved)

| # | Question | Decision |
|---|---|---|
| D1 | **Flag vs module idiom** | Flag and branch are **scope controls** (persisted dropdowns in the shared subnav), *not* a breakdown idiom. The **breakdown is always a list below the chart** — by *module* on Tests/Coverage/Timing, by *transaction LABEL* on Load tests. **Kill the pill bar.** |
| D2 | **Flag scope missing on Tests/Timing** | Solved free by D1 — the subnav flag dropdown appears on every analytics tab (hidden when the project has one flag). |
| D3 | **Flaky + Clusters as tabs** | **Fold into Tests** as L3 sections (your steer). Show a *summary* inline; "nothing to drill" renders as plain inline text (not fake-blue). But **keep the full flaky state-management roster and the cluster detail reachable as drills** — competitor evidence (Datadog flaky-management, Allure Categories) says don't delete the workflow surface. |
| D4 | **Coverage file/package dead-ends** | **Phase it.** Now: package rows **expand inline**, file rows **link to the latest Run's `#coverage-by-file` (existing L5)** — no new screen; non-drilling names stay **non-blue**. Later (Phase 3): a real annotated **file-coverage detail (L4)** — Codecov table-stakes. |
| D5 | **Shared-entity paths** | One canonical path each: **Run** ← any run reference; **Compare** ← any trend point; **Test history** ← any blue test name (Tests roster / Timing slowest / folded Flaky); **Perf-run** ← any perf point/row; **PR** ← any PR row. Same kind of thing = same color = same destination. |
| D6 | **Skeleton = contract of slots** | The shared skeleton is *slots that may be empty or absent*, **not** five sections bolted onto every tab. By-module list renders only when `modules > 1`; empty KPI tiles are suppressed (not "—"); a 1-point trend is an empty state, not a chart; regression overlays/banners are hidden with no baseline. |
| D7 | **Breakdown click = scope the whole tab** | Clicking a breakdown (by-module / by-label) row **scopes the entire tab** — KPI tiles, the primary trend graph, *and* the roster — to that group, not just an in-page roster filter. Server-driven (`?module=…`), the selected row is highlighted (`branch-current`), and a **chip + "← All tests" returns to the unscoped view**. "Bring me to the group, graph included; give me a way back." |
| D8 | **Roster default = the rows that matter** | The detail roster never opens as a flat full dump. Default = **failing + flaky** (a one-glance counts strip above), with passing/skipped folded behind **"Show all N tests"**; **search reaches every test**; **columns are sortable**. No competitor opens with a 1200-row list (Datadog: failed→flaky→slowest + paging; Allure: collapsible suites tree; Codecov Test Analytics: top-N). See the roster-slot spec below. |

### Canonical analytics-tab skeleton (top → bottom)

1. **Shared subnav** — Analytics group ∣ Manage group (visual divider), plus **scope controls: branch ▾ + flag ▾** (persisted across tabs; flag hidden when single-flag).
2. **KPI tile row** (`.stat-row`) — 3–4 tiles, each `value + Δ vs prev run`, color-coded. Δ suppressed on first run. Empty tiles suppressed.
3. **Latest run/report line** (`.muted`) — blue → **Run (L4)**. (Order fixed: tiles *then* this line, on every tab — Coverage currently has it above; move it down.)
4. **Primary trend** — time/run toggle, regression-since overlay, points → **Compare (L4)**. One trend per tab (Load keeps its 3 charts sharing one toggle). 1-point → "needs ≥2 runs" empty state.
5. **Breakdown list** (`.table`, never pills) — "By module" / "By transaction" rows; first col `tr.mod-row` blue `code.mono`; **row-click scopes the whole tab** (KPI tiles + trend + roster) to that group via `?module=…` — server-driven, `branch-current` = selected, chip + "← All tests" returns (D7). Hidden when only one group.
6. **Detail roster(s)** — **search-first** header (reaches every entity); default shows only **failing + flaky** with a counts strip, the rest behind **"Show all N"**; **sortable columns**; **blue entity names → L4 entity**. Always the bottom slot (D8).
7. **Consistent empty state** (`.empty`) per section.

### Final tab list & L0–L5 path (after folding Flaky + Clusters into Tests)

| Tab | Path |
|---|---|
| **Overview** (L1) | L0 Board → **L1** → section jumps to tabs / trend point → Compare / run·PR rows → Run·PR (L4) |
| **Tests** | L2 tiles+trend → **L3** by-module · all-tests roster · *Flaky section* · *Clusters section* → **L4** Test history / Compare / Run → L5 |
| **Coverage** | L2 tiles+trend → **L3** by-module · by-package (inline expand) · worst-covered files → **L4** Run (file → `run#coverage-by-file`, L5) / Compare |
| **Test timing** | L2 tiles+trend → **L3** timing-by-module · slowest roster → **L4** Test history / Compare *(see Open decision T)* |
| **Load tests** | L2 tiles + latency/throughput/error trends → **L3** by-**label** list · recent-perf-runs roster → **L4** Perf-run / Compare → L5 per-label |
| **Manage** (Owners · Triage · Alerts · Settings · Members · Ingest) | write-paths; deliberately outside the analytics skeleton |

### Build plan — shared fragments (frontend, buildable on existing CSS)

- `fragments/components :: kpiTile / emptyState / latestRunLine / entityLink` — promote the markup that's already duplicated 4–5×.
- Static `/static/js/trend.js` exposing `window.__trend(canvas, {datasets, onPoint, overlay})` on the existing `window.__chart` — kills ~600 lines of copy-pasted Chart.js and the `healthOverlay` drift. (Honor `[[` → `[ [`; mirror exposure into the shadowing test `application.yml`.)
- By-module/roster unify **by convention, not one table fragment** (columns legitimately differ): always a list, first col `mod-row` blue name, `branch-current` selected. Retire `.subnav.module-filter` as a breakdown.

### Priorities

- **P0 (predictability — the reconcile core):** D1 flag/branch → subnav dropdowns + kill pill bar; D3 fold Flaky/Clusters into Tests; D5 canonical entity links (esp. Timing slowest name → test history, folded-flaky name → test history); D6 empty/first-run states everywhere; fix tile/latest-line order.
- **P1 (dead-ends + drift):** D4 coverage inline-expand + file→Run L5 (and de-blue non-links); extract `trend.js` + the four fragments; Coverage "Recent reports" list (reach older coverage runs); drop Coverage's redundant time/run toggle.
- **P2 (consumer depth):** failures spine (KPI failures tile → Run.Newly-failing; L0 red card "broken-since" → Run.Failures); cluster member → test history + onset commit; flaky "known vs new" signal; per-test sparkline + retry count in roster.
- **P3 (beyond reconciliation — net-new):** annotated file-coverage detail (L4) + source/blame/rerun links; Load-tests percentile selector, SLA threshold lines, baseline selector, per-label trend (L4) + perf Compare; Timing "got-slower" Δ column.

### All-tests roster slot — panel spec (2026-06-30)

A 6-role panel (UX/IA director, QA, Developer, Competitive analyst, Frontend/design-system,
Restraint skeptic) stress-tested the bottom roster slot. Convergence: **a flat 1200-row dump
has no job** — it duplicates the folded Flaky/Clusters sections and the Test-timing tab, and
no competitor opens with one. The roster's *unique* job on this tab is two things: **"what
failed in this run that isn't a cluster/known flake"** + **"find a test by name → its
history."** The Skeptic argued cut-to-typeahead; director resolved **shrink** because a
singleton non-recurring failure appears in neither folded section, so the failures-first
roster is the only on-tab "what just broke."

- **P0 (done on `new-tests`):** default to the **matters-view** — failing + flaky rows, with a
  one-glance counts strip (`X failing · Y flaky · Z skipped · W passed`); passing/skipped folded
  behind **"Show all N tests"**; **search-first** header reaches every test; **sortable columns**;
  D7 module-scoping (tiles + trend + roster) with chip + back-to-all. Blue name → test history;
  flaky badge = read-only anchor to the Flaky section (no quarantine action in rows).
- **P1:** htmx-lazy "show all" (don't ship 1200 `<tr>` + duplicated `data-search`; server-filter
  on expand); **novelty marker** (NEW regression / STILL-FAILING ×n / FIXED — cheap, prev run
  already loaded) + **Δduration vs prev** on regressed rows; promote the module-scoped trend to a
  SQL aggregate (it currently loads each run's cases).
- **P2:** inline one-line failure-message snippet + "cluster" anchor on failed rows (the *why*
  without a navigation; full trace stays on test.html/run).
- **P3 (aspirational, likely infeasible):** per-test coverage cell (the "row Datadog + Codecov
  each show half of") — coverage is per-run/per-file, **not per-test**, in the data model.
- **Guardrails (Restraint, adopted):** no quarantine/fail-rate/flaky-commit columns (Flaky section
  owns those); no per-test sparkline in rows (test.html owns that); don't rebuild the Timing tab
  as a sortable duration view; no speculative paging infra.

### Unresolved tensions (your call)

- **Decision T — does Test timing stay a tab? → RESOLVED: keep it a tab.** (The Restraint Skeptic argued it's just *Tests sorted by duration* → merge, six tabs become four; the Performance Engineer kept them distinct as different subjects. Per epic #390's framing — "unify the four tabs," not delete one — Test timing stays its own tab and conforms to the canonical skeleton.)
- **Flaky management surface placement** — folded summary lives in Tests; where does the full **state-management roster** live — an in-Tests "manage flaky" expansion, or a Manage-group tab? (Competitor says keep it a real surface either way.)
- **Per-test status vocabulary** — rosters hand-roll a `.badge`; runs use `statusPill`. Unify to one, or keep `.badge` for denser per-test rows (documented)?
