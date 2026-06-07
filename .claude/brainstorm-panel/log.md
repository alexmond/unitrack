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
