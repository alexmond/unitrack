# Brainstorm Panel — log

Evidence from past panels in this repo. Bias future proposals with it; it's not law.

## 2026-06-07 — Next feature to build (prioritization / open ideation)
- Proposed roster: Product Strategist (director), Competitive Analyst, Spring Boot Architect, DevEx/Adoption Advocate, YAGNI Skeptic. User added: **Tester (QA)** and **Automation Tester (SDET)**. User removed: none.
  - Signal: the two consumer/practitioner roles the user added (Tester, SDET) carried real weight — SDET surfaced the eventual #1 (machine-readable gate); Tester surfaced the #3 (blame-link). For a *product/roadmap* target in this repo, **seat Tester + SDET by default**.
- Style: **swarm → director-led**, 3-round cap (converged in 2). Fit well: swarm gave 7 independent rankings, director reconciled by value×differentiation÷effort. Keep for prioritization targets.
- Outcome top-3: 1) **Machine-readable quality-gate result** (XS — GateController/QualityGateResult already exist; gap = CI-consumable exit-code contract + by-commit lookup + wrapper script) 2) **Unit-test performance** (slowest-tests + duration trends; S, read-side over already-ingested durationMs; Round-1 Condorcet winner) 3) **First-failing-commit blame link** (S if scoped to "since last green on same branch", SHAs only).
- Recurring critiques to expect here:
  - **Effort re-grading.** R1 estimates were optimistic; the Architect's R2 pass moved PR-comment M→L and blame-link S→M. **Always run an Architect effort-validation pass before locking estimates.**
  - **Adoption-vs-internal-value split.** DevEx/SDET push external-pull features (gate contract, PR comment); Product/Competitive/Tester push internal value (perf, triage). With ~0 external users, the cheap *adoption unlock* (machine-readable gate) won by being XS, not by winning the philosophical argument.
  - **YAGNI consistently vetoes** RBAC (half-a-feature for a solo maintainer) and MCP (#54, speculative). Don't lead with those.
- Repo quirks worth respecting: per-test **durationMs already ingested** (perf features are pure read-side); QualityGateService baseline = latest prior run on base-branch + same flag; UniTrack stores commit **SHAs only** (no git diff/history) — anything "blame"/"first-failing" must be scoped to stored runs, not git.

## 2026-06-08 — Perf-test ingestion design (design / greenfield → epic+tickets)
- Proposed roster: Performance Engineer, Market Analyst (web research), Spring Boot Architect (director), Charts Advocate, SDET, YAGNI Skeptic. User added/removed: none (approved as-is).
  - Signal: the perf-domain version of "Tester" (Performance Engineer) + SDET were both pivotal — SDET drove the per-label "store now" call and the 422 status-code semantics; Perf Engineer set the headline metric (p95 + error rate). Confirms the seat-Tester+SDET default for product/design targets.
- Style: **swarm → director-led**, 3-round cap (converged in 2). Worked well for a design target: swarm gave 6 lenses incl. live market research; director synthesized into a data model + ordered ticket plan.
- Outcome: data model (perf_run + perf_transaction), JMeter JTL CSV first (k6 fast-follow, Gatling deferred), extend /api/v1/ingest with a `perf` param + PerfResultParser abstraction (copy CoverageParser), regression reuses baseline pattern (p95 +15% / tput −10% / err >1%), project perf trends UI. MVP first PR = data model + JMeter parser + ingest wiring.
- Recurring (confirmed again): **Architect effort-validation pass is essential** — it re-graded the JMeter JTL parser M→L (percentile computation from per-sample data is an aggregation pipeline, not a parse). Always run it.
- Note (domain): JMeter JTL is CSV per-sample (compute percentiles server-side); k6 JSON summary is pre-aggregated (trivial); Gatling simulation.log is brittle/undocumented — defer. Run-vs-run baseline comparison (BlazeMeter's signature) is table-stakes; reuse UniTrack's per-commit baseline. Differentiation: self-hosted + free + perf regression unified with tests+coverage.
