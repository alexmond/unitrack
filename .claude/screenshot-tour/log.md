# Screenshot tour log

Per-run log for the `screenshot-tour` skill in this repo. Append one entry per run; graduate
stable invariants into a `## Screenshot tour` block in `CLAUDE.md` after ~3 runs.

## run-1  2026-06-27  unitrack
- product-type: web (Spring Boot 4 + Thymeleaf) + cli
- aspects-planned: 9   aspects-captured: 9   aspects-fallback: 1 (CLI)
- tools-used: Selenium-Java (8 web pages via a new `ScreenshotTourTest`, reusing the existing
  `FullPageScreenshotUiTest` driver/login/full-page pattern), chromium+HTML terminal still (1 CLI gate — Freeze/Go not installed)
- steering: user approved all 9 aspects, dark theme, demo dataset (over live: live has only healthy public projects).
- gotchas:
  - Surefire CWD is the **module dir**, so `Path.of("presentation")` writes to `unitrack-web/presentation/` — move to repo-root `presentation/` after the run.
  - The `@Tag("ui")` tour test is excluded by default; run with `-DexcludedGroups=`.
  - The demo seeder timestamps every run at seed time (`now`), so the **time-axis trend clusters**; capture the overview trend in **"By run" mode** (click `#trendModeToggle button[data-mode='run']`) for demo data — it reads as a clean regression/recovery arc. Time-axis shines only on real CI data spread over days (e.g. the live jhelm project).
  - Demo latest runs all PASS, so the board's "broken since" line and run-detail stacktraces don't appear on demo data — would need a project whose latest run fails. Captioned to what's actually shown.
  - SSE keeps the connection open → `networkidle` waits never settle; use `domcontentloaded` + a fixed sleep for Chart.js. (Selenium `document.readyState` + sleep works.)
  - Don't `mv` the output dir mid-run — the test writes each capture incrementally; moving mid-run races and aborts later steps. Wait for completion, then consolidate.
- outcome: committed-at presentation/ (01–09 + contact-sheet.png + tour.md); recipe at unitrack-web/.../ui/ScreenshotTourTest.java
- graduated: none yet (first run)

## run-2  2026-06-27  unitrack  (new-features refresh)
- product-type: web (Spring Boot 4 + Thymeleaf) + cli
- aspects-planned: 11   aspects-captured: 11 (10 web via Selenium + 1 CLI still reused from run-1)
- steering: user approved Full 11, "seed a regressed project", and a stubbed AI card. Added two ★new
  aspects — #6 AI root-cause, #7 Load tests — and foregrounded the trend + broken-since signals.
- recipe changes (ScreenshotTourTest):
  - AI card needs the feature *on*: added `spring.ai.anthropic.api-key=…` to the test properties and a
    `@TestConfiguration` wiring a `@Primary` `StubChatModel` (from `web/ai/support`, builder's pattern) so
    the card renders a fixed answer — no key, no network, no cost, deterministic.
  - `shootAnalysis()`: open all `<details>` (the AI button lives in the collapsed cluster body), click
    `.ai-slot button`, then WebDriverWait until `#ai-0` is non-blank before capturing.
- seeder changes (DemoDataSeeder, demo-only):
  - a genuine multi-test cluster (2 distinct tests, identical type|message — demo failures carry no
    stack frame, so type+message alone is the signature) so the "Analyze with AI" button shows.
  - `seedRegressed()` = a project that breaks ~2w ago and stays red (latest run fails) + `backdateRuns()`
    via **JdbcTemplate native UPDATE on test_run.created_at** (the column has no `@Setter`, and ingest
    always stamps `now`) so the board's "broken since" + the trend onset render against real history.
- gotchas:
  - Running the tour needs `-pl unitrack-web -am -Dsurefire.failIfNoSpecifiedTests=false`. WITHOUT `-am`
    the run fails to resolve `unitrack-core`/`unitrack-parent` (dev-verify runs `verify`, not `install`,
    so they're absent from `~/.m2`). Cost me one failed run.
  - run-1 gotchas still hold: surefire CWD = module dir → captures land in `unitrack-web/presentation/`,
    consolidate to repo-root `presentation/` after; `@Tag("ui")` excluded unless `-DexcludedGroups=`.
  - backdating 14d rendered as "broken since … · 1w" (label rounds down), not "2w" — signal is correct,
    just a softer number. Bump the offset if you want the bigger figure.
- outcome: committed-at presentation/ (01–11 + contact-sheet.png + tour.md, renumbered); recipe +
  seeder updated. Pending: commit the seeder/recipe to a branch+PR.
- graduated: none yet (run-2). After a 3rd run, lift the canonical aspect list + the `-am`/CWD/seed
  recipe into a `## Screenshot tour` block in CLAUDE.md.
