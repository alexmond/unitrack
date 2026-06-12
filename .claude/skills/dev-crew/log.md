# dev-crew run log

Append-only. The learning loop reads this to graduate lineups, re-tier models,
and detect missing roles. One entry per run. Newest at the bottom.

## Entry schema

```
## <run-id>  <yyyy-mm-dd>  <repo>
- category: <feature|bugfix|refactor|schema-change|perf|release|infra|...>
- task: <one line>
- roster: architect(opus) -> dev(sonnet) -> qa(sonnet) -> deployer(haiku)
- mode: <autopilot | checkpoint | co-drive>
- outcome: <shipped | blocked-at-<gate> | abandoned>
- per-role:
  - architect: <result, notable decisions>
  - dev: <result, scope flags>
  - qa: <pass/fail per criterion; defects caught + where introduced>
  - deployer: <dry checks; commands staged; go given by user? y/n>
- gate-events: <qa loops, deploy holds, escalations>
- steering: <mid-run injections: what you changed, which phase, and why — the learning loop reads this>
- model-fit: <any role over/under-powered for this run>
- missing-role: <recurring failure class with no owner, if any>
- graduated: <lineup/re-tier/new-role changes written back this run, if any>
```

---

<!-- entries below -->

## 170-per-test-page  2026-06-11  unitrack
- category: feature
- task: per-test page — status timeline across runs + duration sparkline + first-failing blame; link test names from flaky/run/perf
- roster: architect(opus) -> dev(sonnet) -> qa(sonnet) -> ui-reviewer(sonnet) -> deployer(haiku)
- mode: autopilot
- outcome: shipped (PR #205)
- per-role:
  - architect: clean route call (enrich existing /projects/{id}/test, no fake /test/{id} id); new TestTimelinePoint (left DurationPoint alone); clusters correctly ruled out-of-scope (REST/MCP contract); 13 testable done-criteria
  - dev: implemented all 13, build green; one tight follow-up loop to add the web MockMvc test qa flagged (+4 tests)
  - qa: PASS all 13 (JaCoCo 80.6%); caught a missing web-layer HTTP test for criteria 4–7
  - ui-reviewer: PASS; caught F2 (h1>code.mono rendered the title at 13px body size) + F1 (timeline cells lacked aria-label) — both fixed in a dev polish loop. F3 (page lacks subnav) noted as pre-existing/out-of-scope
  - deployer: dry checks green (102 tests); staged commit/push/PR; held at deploy gate for go
- gate-events: deploy gate held once (got go); no qa-fail loop (gap was advisory, routed proactively)
- steering: user added the ui-reviewer role mid-run ("when UI is involved we need at least one more role") — minted dc-ui-reviewer (probationary) and inserted it after qa
- model-fit: tiers fit well; ui-reviewer on sonnet caught real craft issues; dev on sonnet handled the feature cleanly
- missing-role: none new
- graduated: minted ui-reviewer (probationary, repo-level). NOT yet graduating a feature lineup into CLAUDE.md (needs >=3 stable runs); standing UI-role steer captured by the role's registry entry
