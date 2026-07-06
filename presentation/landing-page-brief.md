# UniTrack — Landing Page Brief

*Output of an 8-role brainstorm panel (swarm → director-led, 1 round, strong convergence). Serves three audiences: JVM/build developers, QA/test engineers, performance engineers.*

---

## The one decision everything hangs on

**Unified hero + three co-equal proof lanes below** (7 of 8 votes: BLEND). Not three separate doors — the wedge *is* "one tool does all of it," so the top of the page must state the union, then let each persona find their pain named within one scroll.

**The wedge sentence** (the panel's north star — a line no single competitor can truthfully print):

> **UniTrack is the only free, self-hosted tool that shows a coverage drop, a flaky test, and a p99 regression for the *same commit*.**

---

## 1. Project summary

**Elevator line:**
UniTrack is a self-hosted server that ingests the JUnit results, coverage, and benchmark output your CI already produces on every run, and shows how tests, coverage, and performance move over time — per project, branch, and commit.

**What it is:** Allure-meets-Codecov for the JVM — open-source, self-hosted, one dashboard for test trends, per-file coverage, flaky detection, quality gates, failure triage, and load/perf regressions.

**Who it's for:** JVM teams who want test + coverage + performance intelligence **on their own box**, without a per-seat SaaS bill and without their source or coverage data leaving their network.

**Why it wins (3 pillars):**
1. **All-in-one signal** — tests + coverage + perf + flaky on one commit timeline. No competitor in the set does all four; the strong ones (Codecov, Datadog, Trunk) are SaaS and cover only one or two axes.
2. **Yours, not rented** — Docker / Kubernetes, your Postgres, your network. Free, open-source, no seat limits, data stays home.
3. **Enforces, doesn't just chart** — quality gates fail the build via a GitHub commit status + PR comment; badges make the state public.

---

## 2. Landing-page data

### Hero
- **Headline:** *Your tests, coverage, and performance — one self-hosted timeline.*
- **Subhead:** *Allure-meets-Codecov for the JVM. Upload the Surefire/JaCoCo XML you already produce and get trends, flaky detection, quality gates, and perf regressions — open-source, on your own box.*
- **Above-the-fold stack-fit tags** (dev bounces if artifacts aren't named): `Java 21 · Spring Boot` · `Surefire/JUnit XML` · `JaCoCo/Cobertura/LCOV/OpenCover` · `JMeter/k6/JMH` · `Self-hosted`

### Per-persona feature → benefit (the three lanes)
- **Developers —** *Enforce coverage and quality gates in CI with a GitHub commit status, so a merge that drops coverage or breaks a gate fails the check instead of shipping.*
- **QA / Test engineers —** *Flag tests that pass and fail on the same commit and cluster failures by cause, so you triage one root cause instead of re-running a hundred red tests.*
- **Performance engineers —** *Track p50/p90/p99 and throughput per commit with regression-since baselines, so a JMH or load slowdown shows up as a diff, not a surprise in prod.*

### Proof / spec strip (a dev trusts these more than adjectives)
- Self-hosted: Docker or Kubernetes, your Postgres, your network. **Source never leaves your infra.**
- Open source, free, **no seat limits.**
- Reads what CI already produces: Surefire/JUnit XML, JaCoCo/Cobertura/LCOV/OpenCover, JMH.
- Any CI: upload via the CLI or the GitHub Action — **no pipeline rewrite.**
- Spring Boot 4 / Java 21. Live badges, alerts, and an MCP server included.

### Competitor framing — a tight 6×6 matrix (not the exhaustive 10-tool table)
Rows: **Codecov · Datadog Test Optimization · Trunk · Allure · SonarQube · UniTrack.**
Columns: **Tests · Coverage · Performance/JMH · Flaky · Free self-host.**
UniTrack is the **only full-checkmark row**; the two decisive columns are **Performance/JMH** (UniTrack-only) and **Free self-host** (UniTrack-only among the capable tools).
Plus a one-line "why not just use X" strip: *Codecov has no perf. Datadog/Trunk aren't self-host-free and have no coverage. Allure/ReportPortal have no coverage. SonarQube isn't a test report.*

### Claims to NOT make (credibility guardrails — the Skeptic + Analyst, adopted in full)
- ❌ "Polyglot / multi-language" in the hero → proof is JVM-only. Honest line lower: *"Built for the JVM today (JUnit + JaCoCo); format-agnostic ingest by design."*
- ❌ "AI-powered" as a lead → it needs the user's own Anthropic key. Label it *"Optional AI triage (bring your own Anthropic key)."*
- ❌ "GitHub App / Checks API" → only commit-status + PR comment ship. Say exactly **"GitHub commit status + PR comments."**
- ❌ "Enterprise / team / SSO / org-ready" → it's 0.x, solo-maintained. Cut all enterprise framing.
- ❌ Flaky detection as *the* headline differentiator → rivals match or beat it (auto-quarantine/ML). Frame flaky as **table-stakes included**; the headline is **unification + perf**.
- ✅ Weaponize maturity instead of hiding it: a small banner — *"0.x, self-hosted, open source. Your server, your data — read every line."*
- Banned words: *intelligence, observability platform, seamless, actionable insights, enterprise-grade, revolutionary, next-gen.* Prefer a number, a format name, or a command.

### CTAs
- **Primary (repeated in nav, hero, final band):** **Try the live demo →** (`unitrack.alexmond.org`) — biggest, lowest-friction proof asset.
- **Secondary:** **Self-host in 5 min** (scrolls to copy-paste quickstart).
- **Tertiary:** **Star on GitHub** (nav badge + footer).

---

## 3. Landing-page design

### Section order (top → bottom)
1. **Sticky nav** — logo · Demo · Docs · GitHub-stars badge · **Try the live demo** button.
2. **Hero** — headline + subhead + stack tags + dual CTA (see composition below).
3. **"Sound familiar?"** — three one-line pains, one per persona (dev / QA / perf).
4. **Persona tab-band** — `Developers · QA · Perf` tabs; each swaps a benefit line + the matching screenshot. Dev tab default (largest audience). CSS `:target`/tiny JS, no extra pages.
5. **The unified board** — the money screenshot (project overview: gate verdict + pass/coverage trend) with the "one commit timeline" caption.
6. **Competitor matrix** — the 6×6, UniTrack the only full row + the "why not just use X" strip.
7. **Differentiator strip** — failure clustering → AI root-cause (one short GIF for the "aha"), labeled honestly ("optional AI triage, bring your own key").
8. **Self-hosted & integrations** — Docker/K8s, GitHub commit status + PR comment, Slack/Teams/Discord alerts, badges, MCP.
9. **Quickstart** — copy-paste GitHub Action YAML (5 lines) **and** the CLI one-liner + `docker run` tab → CTA.
10. **Final CTA band** (repeat primary) + footer (repo, docs, live demo, live badges).

### Hero composition
- Headline top-left, left-aligned; subhead beneath; stack tags as a chip row.
- **Two visuals side by side** (resolves the Designer↔Dev split):
  - **Anchor:** the **project-overview board** screenshot (gate verdict + pass/coverage trend) — shows the "all-in-one timeline" wedge at a glance, legible in one look.
  - **Beside/below it:** the **failing quality-gate + PR-comment** screenshot next to a **5-line CI snippet** — the Dev Advocate's key insight: a dashboard reads as *someone else's data*; a failing gate reads as *this will guard my PR*. Comprehension **and** "it closes the loop in my CI," above the fold.
- Primary CTA **Try the live demo →** (bright); secondary ghost **Self-host in 5 min**; small GitHub-star link beneath.

### Perf's placement — the one deliberately-split call (see Unresolved Tensions)
Perf stays a **co-equal third lane** (tab + a proof band with the real p50/p90/p99 + regression-at-`a4`-then-recovery screenshot) because it's the **cleanest differentiator** and the wedge sentence literally needs it. But it does **not** get a hero headline of its own, and the JMH story is stated plainly (no depth overclaim). Honest caption: *"real run, real regression — caught and recovered; click through on the demo."*

### Visual/asset rules
- Hero + persona-tab images: **static PNG** (fast LCP, crisp). One short **GIF** only for the AI-root-cause moment.
- **No embedded iframe** of the live app (slow, framing risk) — drive demo traffic out via the CTA.
- **Live badges are real embeds** (status/coverage/flaky) — they animate trust because they're genuinely live.
- Every persona claim gets a **live-demo deep link** to that exact view (flaky, clusters, load-tests).

---

## Plan that ran
- **Team:** Product-Marketing Lead (director) · Competitive Analyst · Developer-Buyer Advocate · QA Advocate · Performance Advocate · Conversion Designer · Technical Copywriter · Anti-Hype Skeptic.
- **Style:** swarm → director-led, 1 round (strong convergence, matching this repo's pattern).
- **Bar:** a dev, QA lead, or perf engineer landing for 30s sees their pain named, believes the all-in-one + self-hosted wedge, clicks to demo/quickstart — zero unbacked claims.

## Changelog (what each voice drove)
- **Wedge sentence** ← Competitive Analyst (same-commit tri-signal) + Lead + Perf (no-rival perf).
- **Unified hero, not three doors** ← 7/8 BLEND vote.
- **Hero = board + failing-gate/CI-snippet side by side** ← Designer (board) reconciled with Dev Advocate (failing gate reads as "guards MY PR").
- **Persona tab-band, Dev default** ← Designer; **QA gets a co-equal band, not last** ← QA Advocate.
- **Overclaim guardrails** (polyglot/AI/Checks/enterprise/flaky-as-headline) ← Skeptic + Analyst, adopted in full.
- **Copy** (headline, subhead, feature→benefit, proof bullets, CTAs) ← Copywriter.
- **Primary CTA = live demo** ← Designer (cheapest, strongest proof).

## Unresolved tensions (for the user to overrule)
1. **Perf prominence — the one real split.** Perf Advocate + Analyst: give perf a visible co-equal lane (it's the cleanest wedge, "runs unopposed"). Skeptic: perf is "real but niche," mostly a demo screenshot — bury it under "More," labeled *early*, don't headline it. **Director call:** kept perf as a co-equal lane **in the wedge + a proof band**, but adopted the Skeptic's honesty guardrails (no perf headline, no JMH-depth overclaim). If you consider perf too thin to co-headline, demote it to a "More capabilities" card and the design still holds.
2. **Feature count.** Skeptic wants only 4–5 lead features (coverage+gates, trends, flaky, clustering) with the rest behind a "More →" link. The brief leads with the three-persona pillars but keeps AI/perf/alerts/MCP as below-the-fold bands — a middle path; tighten to the Skeptic's 4–5 if the page feels crowded.
