# UniTrack — UI data map

What each screen displays today, grouped by navigation level. Source of truth: the
controllers in `web/ui/` + templates in `resources/templates/`. Use this for the
analytics-page unification work (#390) and to spot gaps/duplication.

Levels:
- **L0** — global / landing (no project context)
- **L1** — a single project (the subnav tabs)
- **L2** — drill-down from a project page (a run, a test, a comparison)
- **L3** — drill-down from L2 (export, share)

Cross-cutting chrome:
- **Topbar (global):** Projects · Status · Owners · Ops* · Audit* · Ingest* · profile · theme toggle  (`*` = admin)
- **Project subnav (L1):** Overview · Tests · Coverage · Flaky tests · Test timing · Load tests · Failure clusters ‖ *(write-only)* Ingest · Triage rules · Owners · Alerts · Settings · Members

---

## L0 — Global

### `/` — Projects board (`index.html`)
- **Summary stats:** project count · failing gates · flaky total · avg coverage %
- **Per-project health card** (`ProjectHealth`, sorted failing-first): project name · branch · status · pass % · coverage % · flaky count · last-run time · and when red: broken-since date · days-red label · runs-red count
- **Drills to:** L1 project overview

### `/status` — System status (`status.html`)
- System status · Components (health contributors) · Build (version/info)

### `/owners` — Owner accountability, cross-project (`owners-global.html`)
- Per owner: Failing · Flaky (aggregated across all projects)
- **Drills to:** project Owners (L1)

### `/ops` * — Operations (`ops.html`)
- Operational counters · Recent failures (across projects)

### `/audit` * — Audit log (`audit.html`) · `/ingest` * — Ingest history (`ingest.html`)
### `/profile`, `/login`, `/signup` — account

---

## L1 — Project

### `/projects/{id}` — Overview (`project.html`)
- **Filter:** branch selector (dropdown, when >1 branch)
- **Hero:** pass-rate donut (pass% + fail%) · status pill · passed/total · coverage % · latest-run link (commit · branch · time)
- **Push-from-CI** help (collapsible upload snippet)
- **Trends chart:** passed / failed / skipped + line-coverage %; Over-time / By-run toggle; "regressed since" overlay + reporting-gap band; click a point → compare vs previous
- **Coverage by module** (multi-module; blue links → coverage?module)
- **Coverage by flag** (when >1 flag): flag · line cov · latest · status
- **Branches:** branch · status · pass % · coverage · runs · last seen (ephemeral collapse behind "show all")
- **Pull requests:** PR · branch · status · coverage · runs · latest
- **Recent runs:** when · branch · flag · commit · status · tests · failed · skipped · pass % · coverage · duration
- **Compare runs** form (base/head)
- **Drills to:** run (L2) · compare (L2) · coverage?module · PR (L2) · branch-scoped overview

### `/projects/{id}/tests` — Tests (`tests.html`)
- **KPI tiles (Δ vs prev run):** Pass rate · Failures · Suite time · Tests
- **Latest-run line** (commit · branch · time)
- **Test results trend:** passed/failed per run; Over-time / By-run toggle; regression overlay; click → compare
- **Tests by module** (list; click filters the roster) — module · tests · pass % · failures · skipped
- **All tests** (search box): Test → history · Status · Duration · Flaky → flaky page
- **Drills to:** single-test history (L2) · flaky · compare

### `/projects/{id}/coverage` — Coverage (`coverage.html`)
- **KPI tiles:** Line (+Δ) · Branch · Instruction · Method
- **Latest-report line** (run link · branch · time)
- **Line-coverage trend:** Over-time / By-run toggle; click → compare
- **Coverage by module** (list; click scopes the tables below; "show all" clears)
- **By package:** package · line % · bar · branch % · lines
- **Worst-covered files:** file · line % · bar · covered · missed

### `/projects/{id}/performance` — Test timing (`performance.html`)
- **KPI tiles:** Suite time (+Δ) · Slowest test · Runs tracked
- **Suite-time trend:** Over-time / By-run toggle; click → compare
- **Timing by module** (list; click filters slowest list) — module · tests · total time · avg
- **Slowest tests:** test → history · suite · duration · status
- **Drills to:** single-test history (L2)

### `/projects/{id}/perf` — Load tests (`perf.html`)
- **Filter:** perf-flag pills (perf series)
- **Regression banner:** sustained p95 latency step (onset commit/date · baseline→recent · σ)
- **KPI tiles (Δ vs prev):** p95 latency · Throughput · Error rate · Samples
- **Charts (shared Over-time / By-run toggle; click → perf-run):** Latency (p50/p90/p99) · Throughput · Error rate
- **Recent perf runs:** when · flag · commit · format · samples · p95 · throughput · errors → details
- **Drills to:** perf-run detail (L2)

### `/projects/{id}/flaky` — Flaky tests (`flaky.html`)
- Test · State · Flaky commits · Failures · Fail rate · Last failure · Action (state management)

### `/projects/{id}/clusters` — Failure clusters (`clusters.html`)
- Clusters (grouped current failures) · Recurring failures · (AI root-cause analyze)

### Project management tabs (write access)
- `/projects/{id}/owners` — Test owners · Scorecard · Rules · Add rule
- `/projects/{id}/triage` — triage rules
- `/projects/{id}/alerts` — alert channels
- `/projects/{id}/settings` — gate config, visibility, base branch
- `/projects/{id}/members` — members/roles
- `/projects/{id}/ingest` — per-project ingest history
- `/projects/{id}/pr/{pr}` — PR detail: Coverage change · Runs on this PR

---

## L2 — Drill-down

### `/runs/{id}` — Run detail (`run.html`)
- **Header stats:** Tests · Passed · Failed · Skipped · Duration · Line cov
- **Failures:** test · baseline · now · Δ (regression vs baseline)
- **Suites:** suite · tests · failures · errors · skipped · time
- **Slowest tests:** test · duration
- **Coverage change:** file · baseline · this run · Δ pp
- **Coverage by module:** module · line % · lines · files
- **Coverage by file:** file · line % · bar · covered · missed
- **Nav/actions:** prev/next run · export · AI analyze · share link
- **Drills to:** compare · single-test history · export (L3) · share (L3)

### `/perf-runs/{id}` — Perf-run detail (`perf-run.html`)
- Per-label breakdown · prev/next perf-run nav

### `/projects/{id}/test?className=&name=` — Single test history (`test.html`)
- Status timeline · Duration trend · History (per-run rows)

### `/compare?base=&head=` — Compare runs (`compare.html`)
- Newly failing · Fixed · Still failing · Timing changes (test · base · head · Δ)

---

## L3 — Drill-down from a run
- `/runs/{id}/export` — run export (`run-export.html`)
- `/share/{token}` — public read-only shared run view

---

## Observations (for #390 unification)
- **KPI-tile row** is now consistent on Tests / Coverage / Test timing / Load tests; Overview uses the donut hero instead.
- **Primary trend** (Over-time / By-run toggle + clickable points) is consistent on Overview / Tests / Coverage / Test timing / Load tests.
- **"By module" list** (blue clickable names) is now on Overview (coverage) / Tests / Coverage / Test timing; Load tests groups by perf-flag instead of module.
- **Module vs flag:** flags are separate upload series (perf still uses flag pills); modules are the within-run breakdown (explicit `module` #393, else package-derived).
- **Gaps to consider:** Test timing & Tests lack a flag scope control (fixed to the default rollup); Load tests is the only analytics page still using a top pill bar (perf-flag).
