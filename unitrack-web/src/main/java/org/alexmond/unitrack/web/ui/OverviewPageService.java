package org.alexmond.unitrack.web.ui;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.PerfRun;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.FlakyTestService;
import org.alexmond.unitrack.report.PerfStepDetectionService;
import org.alexmond.unitrack.report.PerfStepSignal;
import org.alexmond.unitrack.report.QualityGateResult;
import org.alexmond.unitrack.report.QualityGateService;
import org.alexmond.unitrack.report.ReportingService;
import org.alexmond.unitrack.report.TestRegressionResult;
import org.alexmond.unitrack.report.TestRegressionService;
import org.alexmond.unitrack.web.ui.view.AspectCard;
import org.alexmond.unitrack.web.ui.view.OverviewPage;
import org.alexmond.unitrack.web.ui.view.OverviewPage.VerdictChip;
import org.alexmond.unitrack.web.ui.view.TrendView;
import org.springframework.stereotype.Service;

/**
 * Builds the reconciled Overview ({@link OverviewPage}) — a health verdict + four
 * one-signal aspect cards that route into the tabs. Deliberately cheap: two of the four
 * cards (Coverage, Timing) come free from the already-loaded runs, and the Load card is
 * gated behind a single {@code perfFlags} check so a test-only project pays nothing for
 * it (per the 2026-07-04 brainstorm panel's Restraint/Performance lens). Lives in
 * {@code web.ui} so it can reuse the package-private {@link AnalyticsView} helpers the
 * tabs share.
 */
@Service
@RequiredArgsConstructor
public class OverviewPageService {

	/** Line coverage below this reads as "at risk" in the verdict. */
	private static final double COVERAGE_TARGET = 80.0;

	/**
	 * A suite-time delta (seconds) beyond this floats the Timing card up as a concern.
	 */
	private static final double TIMING_WARN_SECONDS = 0.5;

	private static final int TREND_LIMIT = 30;

	private static final String ROLLUP_FLAG = "default";

	private final QualityGateService qualityGate;

	private final TestRegressionService regression;

	private final FlakyTestService flaky;

	private final ReportingService reporting;

	private final PerfStepDetectionService perfStep;

	/**
	 * Assemble the Overview for one project. {@code runs} is the recent runs on the
	 * selected branch (newest first); {@code trend} is the single-flag trend series
	 * (oldest first) for the "Health over time" chart.
	 */
	public OverviewPage build(Project project, Long id, List<TestRun> runs, List<TestRun> trend) {
		String repoCommitBase = AnalyticsView.repoCommitBase(project.getRepoUrl());
		if (runs.isEmpty()) {
			return new OverviewPage(project, "overview", false, "No runs", "lvl-warn",
					"No runs yet — push results from CI to start tracking health.", List.of(), 0, 0, 0, null, null,
					List.of(), new TrendView(false, "Health over time", null, "{}"), repoCommitBase);
		}

		TestRun cur = runs.get(0);
		// Δ vs the previous run OF THE SAME FLAG: recentRuns is branch-scoped but
		// all-flags,
		// so a split-by-module push writes several flag rows for one commit — runs.get(1)
		// can
		// be a different module of the same build, which would make every card's Δ
		// compare
		// two flags. Pick the most recent earlier run sharing cur's flag instead.
		TestRun prev = runs.stream()
			.skip(1)
			.filter((r) -> java.util.Objects.equals(r.getFlag(), cur.getFlag()))
			.findFirst()
			.orElse(null);
		Long curId = cur.getId();

		QualityGateResult gate = qualityGate.evaluate(curId).orElse(null);
		TestRegressionResult reg = regression.diff(curId).orElse(null);
		long flakyCount = flaky.flakyCount(id);
		String brokenLabel = brokenLabel(trend);

		List<AspectCard> cards = new ArrayList<>();
		cards.add(testsCard(id, cur, prev, reg, brokenLabel));
		cards.add(coverageCard(id, cur, prev));
		cards.add(timingCard(id, cur, prev));
		AspectCard load = loadCard(id);
		if (load != null) {
			cards.add(load);
		}
		// Trouble-first: the worst-state card floats to the front so it sits beside the
		// verdict. Stable sort keeps the natural Tests→Coverage→Timing→Load order within
		// a tier.
		cards.sort((a, b) -> Integer.compare(severity(b.level()), severity(a.level())));

		Verdict v = verdict(id, cur, gate, reg, brokenLabel, flakyCount);
		int total = cur.getTotalTests();
		// Donut arcs are all on the TOTAL basis (green=pass, red=fail, amber=skipped), so
		// the
		// green arc must be pass-of-total — passRate() excludes skipped and would erase
		// the
		// amber skipped arc. The centre label keeps passRate() (the headline pass rate).
		double donutPass = (total > 0) ? cur.getPassed() * 100.0 / total : 100.0;
		double failPct = (total > 0) ? (cur.getFailed() + cur.getErrors()) * 100.0 / total : 0.0;
		// Hero pill reflects the gate verdict (not raw test-execution status) so the
		// band's
		// pill, donut colour and word all agree even when tests pass but the gate fails.
		String heroStatus = (gate != null) ? gate.status() : cur.getStatus();

		return new OverviewPage(project, "overview", true, v.word(), v.level(), v.line(), v.chips(), cur.passRate(),
				donutPass, failPct, heroStatus, AnalyticsView.latestRunLine(cur, repoCommitBase), cards,
				trendView(trend), repoCommitBase);
	}

	// --- verdict
	// --------------------------------------------------------------------------

	private Verdict verdict(Long id, TestRun cur, QualityGateResult gate, TestRegressionResult reg, String brokenLabel,
			long flakyCount) {
		boolean runPassed = "PASSED".equals(cur.getStatus());
		boolean gatePassed = (gate == null) || gate.passed();
		int newFail = (reg != null) ? reg.newFailureCount() : 0;
		Double cov = cur.getLineCoveragePct();
		String testsHref = "/projects/" + id + "/tests";
		String coverageHref = "/projects/" + id + "/coverage";

		List<VerdictChip> chips = new ArrayList<>();
		if (gate != null) {
			// Each failing gate rule chips to the tab that OWNS it — a coverage rule
			// routes to
			// Coverage, not Tests (SonarQube's "chip routes into the owning tab"
			// pattern).
			gate.rules()
				.stream()
				.filter((r) -> !r.passed())
				.limit(3)
				.forEach((r) -> chips.add(new VerdictChip(r.detail(),
						r.name().contains("coverage") ? coverageHref : testsHref, "lvl-bad")));
		}
		if (newFail > 0) {
			chips.add(new VerdictChip(newFail + " newly failing", testsHref, "lvl-bad"));
		}
		if (flakyCount > 0) {
			chips.add(new VerdictChip(flakyCount + " flaky", testsHref + "#flaky-section", "lvl-warn"));
		}

		if (!runPassed || !gatePassed) {
			long failures = cur.getFailed() + cur.getErrors();
			String line;
			if (failures == 0 && gate != null) {
				// Gate failed with no test failures (e.g. a coverage rule) — lead with
				// the
				// deciding rule, not a misleading "0 tests failing".
				line = gate.rules()
					.stream()
					.filter((r) -> !r.passed())
					.map(QualityGateResult.RuleResult::detail)
					.findFirst()
					.orElse("quality gate failed");
			}
			else {
				StringBuilder sb = new StringBuilder();
				sb.append(failures).append((failures == 1) ? " test failing" : " tests failing");
				if (brokenLabel != null) {
					sb.append(" · broken ").append(brokenLabel);
				}
				if (newFail > 0) {
					sb.append(" · ").append(newFail).append(" newly red");
				}
				line = sb.toString();
			}
			return new Verdict("Failing", "lvl-bad", line, chips);
		}
		if (flakyCount > 0 || (cov != null && cov < COVERAGE_TARGET)) {
			String line;
			if (cov != null && cov < COVERAGE_TARGET) {
				line = "coverage " + AnalyticsView.fmt1(cov) + "% below target";
				chips.add(new VerdictChip(line, coverageHref, "lvl-warn"));
			}
			else {
				line = flakyCount + ((flakyCount == 1) ? " flaky test" : " flaky tests");
			}
			return new Verdict("At risk", "lvl-warn", line, chips);
		}
		// A configured, passing gate reads "gate green"; with no gate, don't claim one
		// exists.
		String line = (gate != null) ? "gate green" : "tests passing";
		if (cov != null) {
			line += " · coverage " + AnalyticsView.fmt1(cov) + "%";
		}
		return new Verdict("Healthy", "lvl-good", line, chips);
	}

	// --- aspect cards
	// ---------------------------------------------------------------------

	private AspectCard testsCard(Long id, TestRun cur, TestRun prev, TestRegressionResult reg, String brokenLabel) {
		long failures = cur.getFailed() + cur.getErrors();
		long prevFailures = (prev != null) ? (prev.getFailed() + prev.getErrors()) : 0;
		long dFail = failures - prevFailures;
		int newFail = (reg != null) ? reg.newFailureCount() : 0;
		// The Tests card carries the TEST outcome; a coverage-gate breach is the Coverage
		// card's story, not a red Tests pill.
		String status = cur.getStatus();
		String level = (failures > 0) ? "lvl-bad" : "lvl-good";
		String caption;
		if (failures > 0) {
			// "broken" only when the run is actually red (brokenLabel comes from the
			// default-
			// flag trend and could otherwise contradict a green cur).
			caption = (brokenLabel != null) ? ("broken " + brokenLabel)
					: ((newFail > 0) ? (newFail + " newly failing") : (failures + " failing"));
		}
		else {
			caption = "all " + cur.getTotalTests() + " passing";
		}
		String delta = (prev != null) ? AnalyticsView.signedL(dFail) : null;
		return new AspectCard("Tests", "bi-check2-square", "/projects/" + id + "/tests", status,
				Long.toString(failures), level, delta, AnalyticsView.upIsBad(dFail, 0), caption);
	}

	private AspectCard coverageCard(Long id, TestRun cur, TestRun prev) {
		Double cov = cur.getLineCoveragePct();
		Double pcov = (prev != null) ? prev.getLineCoveragePct() : null;
		String value = (cov != null) ? (AnalyticsView.fmt1(cov) + "%") : "—";
		String level = (cov != null) ? AnalyticsView.coverageLevel(cov) : "";
		String delta = null;
		String dir = "flat";
		if (cov != null && pcov != null) {
			double d = cov - pcov;
			delta = AnalyticsView.signed1(d) + " pp";
			dir = AnalyticsView.upIsGood(d, 0.05);
		}
		return new AspectCard("Coverage", "bi-shield-check", "/projects/" + id + "/coverage", null, value, level, delta,
				dir, (cov != null) ? "line coverage" : "no coverage data");
	}

	private AspectCard timingCard(Long id, TestRun cur, TestRun prev) {
		double dur = cur.getDurationMs() / 1000.0;
		String delta = null;
		String dir = "flat";
		String level = "";
		if (prev != null) {
			double d = dur - prev.getDurationMs() / 1000.0;
			delta = AnalyticsView.signed1(d) + "s";
			dir = AnalyticsView.upIsBad(d, 0.05);
			if (d > TIMING_WARN_SECONDS) {
				level = "lvl-warn";
			}
		}
		return new AspectCard("Test timing", "bi-stopwatch", "/projects/" + id + "/performance", null,
				AnalyticsView.fmt1(dur) + "s", level, delta, dir, "suite time");
	}

	/**
	 * The Load card, or {@code null} when the project has no perf data (hide, don't
	 * empty-state — a perf placeholder is clutter on a test-centric Overview). One cheap
	 * {@code perfFlags} query gates the rest.
	 */
	private AspectCard loadCard(Long id) {
		List<String> perfFlags = reporting.perfFlags(id);
		if (perfFlags.isEmpty()) {
			return null;
		}
		String flag = perfFlags.contains(ROLLUP_FLAG) ? ROLLUP_FLAG : perfFlags.get(0);
		List<PerfRun> recent = reporting.recentPerfRuns(id, flag, 1);
		if (recent.isEmpty()) {
			return null;
		}
		PerfStepSignal step = perfStep.detectLatencyStep(id, flag).orElse(null);
		String level;
		String value;
		String caption;
		if (step != null) {
			// Under a regression the headline number is the recent median so it agrees
			// with
			// the caption's "→B" (not the single-latest p95, which can differ).
			level = "lvl-bad";
			value = AnalyticsView.fmt1(step.recentMedian()) + " ms";
			caption = "p95 " + AnalyticsView.fmt1(step.baselineMedian()) + "→" + AnalyticsView.fmt1(step.recentMedian())
					+ " ms";
		}
		else {
			level = "";
			value = AnalyticsView.fmt1(recent.get(0).getP95Ms()) + " ms";
			caption = "p95 stable";
		}
		// Label matches the tab it routes to ("Load tests" in the subnav).
		return new AspectCard("Load tests", "bi-rocket-takeoff", "/projects/" + id + "/perf", null, value, level, null,
				"flat", caption);
	}

	// --- helpers
	// --------------------------------------------------------------------------

	/** Higher = more troubled, for the trouble-first card sort. */
	private static int severity(String level) {
		if ("lvl-bad".equals(level)) {
			return 3;
		}
		if ("lvl-warn".equals(level)) {
			return 2;
		}
		return 1;
	}

	/**
	 * How long the project has been red, from the trend (oldest first) — the trailing
	 * streak of runs with failures, ending at the latest run. {@code null} when the
	 * latest run is green (not currently broken). Computed in-memory from data already
	 * loaded (no query).
	 */
	private static String brokenLabel(List<TestRun> trend) {
		if (trend.isEmpty()) {
			return null;
		}
		int n = trend.size();
		if (trend.get(n - 1).getFailed() + trend.get(n - 1).getErrors() == 0) {
			return null;
		}
		int i = n - 1;
		while (i - 1 >= 0 && trend.get(i - 1).getFailed() + trend.get(i - 1).getErrors() > 0) {
			i--;
		}
		long days = Duration.between(trend.get(i).getCreatedAt(), Instant.now()).toDays();
		if (days <= 0) {
			return "today";
		}
		if (days < 14) {
			return days + "d";
		}
		return (days / 7) + "w";
	}

	/**
	 * The multi-series "Health over time" trend (pass / fail / coverage), reusing
	 * trend.js.
	 */
	private static TrendView trendView(List<TestRun> trend) {
		if (trend.isEmpty()) {
			return new TrendView(false, "Health over time", null, "{}");
		}
		List<String> labels = AnalyticsView.labels(trend.stream().map(TestRun::getShortSha).toList());
		List<Long> runIds = trend.stream().map(TestRun::getId).toList();
		List<Long> times = trend.stream().map(AnalyticsView::epochMilli).toList();
		List<Map<String, Object>> series = List.of(
				AnalyticsView.series("Passed", "#2ea043", trend.stream().map(TestRun::getPassed).toList()),
				AnalyticsView.series("Failed", "#e5534b",
						trend.stream().map((r) -> r.getFailed() + r.getErrors()).toList()),
				AnalyticsView.series("Line coverage %", "#4493f8",
						trend.stream().map(TestRun::getLineCoveragePct).toList(), "y2"));
		// overlaySeries = 1 (Failed) drives trend.js's "regressed since" red wash.
		String cfg = AnalyticsView.trendConfig(labels, runIds, times, series, 1, "tests", "coverage %");
		return new TrendView(trend.size() >= 2, "Health over time", "(pass / fail / coverage per run)", cfg);
	}

	/** The computed health verdict: word + colour + worst-thing line + routing chips. */
	private record Verdict(String word, String level, String line, List<VerdictChip> chips) {
	}

}
