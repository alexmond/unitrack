package org.alexmond.unitrack.web.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.ReportingService;
import org.alexmond.unitrack.report.TestModuleTiming;
import org.alexmond.unitrack.web.ui.view.BreakdownCell;
import org.alexmond.unitrack.web.ui.view.BreakdownRow;
import org.alexmond.unitrack.web.ui.view.BreakdownTable;
import org.alexmond.unitrack.web.ui.view.EmptyState;
import org.alexmond.unitrack.web.ui.view.KpiTile;
import org.alexmond.unitrack.web.ui.view.ScopeBar;
import org.alexmond.unitrack.web.ui.view.TimingPage;
import org.alexmond.unitrack.web.ui.view.TimingRosterRow;
import org.alexmond.unitrack.web.ui.view.TrendView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the {@link TimingPage} on the shared analytics skeleton (mirroring the Tests
 * tab): KPI tiles, a suite-time + test-count trend (test-count on a second axis, since
 * test growth drives suite-time changes), a by-module breakdown with a Δ-vs-previous-run
 * column, and a slowest-tests roster whose rows also carry a Δ-vs-previous-run. All of it
 * scopes to a selected module.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
class TimingPageService {

	private static final String TREND_FLAG = "default";

	private static final int TREND_LIMIT = 30;

	private static final int SLOW_LIMIT = 25;

	private static final EmptyState EMPTY = new EmptyState("bi-stopwatch", "No test timings yet",
			"Suite time and per-test durations appear here once runs are uploaded.");

	private final ReportingService reporting;

	TimingPage build(Project project, Long id, String flag, String branch, String module) {
		String all = "/projects/" + id + "/performance";
		List<String> flags = reporting.testFlags(id);
		String selectedFlag = selectedFlag(flag, flags);
		// Names only (one query) — avoid BranchService.list's per-branch N+1 (#314) here.
		List<String> branches = reporting.branchNames(id);
		// null/unknown branch = all branches (the non-breaking default); a valid one
		// scopes the tab.
		String selectedBranch = (branch != null && branches.contains(branch)) ? branch : null;
		List<TestRun> trend = reporting.trendRuns(id, selectedBranch, selectedFlag, TREND_LIMIT);
		if (trend.isEmpty()) {
			return new TimingPage(project, false, null, all, false, List.of(), null,
					new TrendView(false, "Suite-time trend", null, "{}"), null, EMPTY, List.of(),
					new ScopeBar(all, flags, selectedFlag, branches, selectedBranch, null));
		}
		TestRun cur = trend.get(trend.size() - 1);
		TestRun prev = (trend.size() > 1) ? trend.get(trend.size() - 2) : null;

		List<TestCaseResult> curCases = reporting.allCasesFor(cur.getId());
		List<String> curMods = reporting.moduleOf(curCases);
		String selected = validModule(module, curMods);
		boolean scoped = selected != null;

		long[] curScope = scopeMsCount(curCases, curMods, selected, cur);
		long[] prevScope = prevScope(prev, selected);
		List<TimingRosterRow> roster = roster(curCases, curMods, selected, prev);

		return new TimingPage(project, scoped, selected, all, true, kpis(curScope, prevScope, prev != null, roster),
				AnalyticsView.latestRunLine(cur, AnalyticsView.repoCommitBase(project.getRepoUrl())),
				trend(trend, scoped, selected), scoped ? null : breakdown(id, selectedFlag, cur, prev, null), EMPTY,
				roster, new ScopeBar(all, flags, selectedFlag, branches, selectedBranch, selected));
	}

	/**
	 * The flag to show: the requested one if valid, else the {@code default} rollup, else
	 * the first.
	 */
	private static String selectedFlag(String requested, List<String> flags) {
		if (requested != null && !requested.isBlank() && flags.contains(requested)) {
			return requested;
		}
		if (flags.contains(TREND_FLAG)) {
			return TREND_FLAG;
		}
		return flags.isEmpty() ? null : flags.get(0);
	}

	/**
	 * [suiteMs, testCount] for the scope: the run's stored aggregates unscoped, module
	 * sums scoped.
	 */
	private static long[] scopeMsCount(List<TestCaseResult> cases, List<String> mods, String module, TestRun run) {
		if (module == null) {
			return new long[] { run.getDurationMs(), run.getTotalTests() };
		}
		long ms = 0;
		long count = 0;
		for (int i = 0; i < cases.size(); i++) {
			if (module.equals(mods.get(i))) {
				ms += cases.get(i).getDurationMs();
				count++;
			}
		}
		return new long[] { ms, count };
	}

	/**
	 * [suiteMs, testCount] for the previous run in the same scope, or null when there is
	 * none.
	 */
	private long[] prevScope(TestRun prev, String module) {
		if (prev == null) {
			return null;
		}
		if (module == null) {
			return new long[] { prev.getDurationMs(), prev.getTotalTests() };
		}
		List<TestCaseResult> pc = reporting.allCasesFor(prev.getId());
		return scopeMsCount(pc, reporting.moduleOf(pc), module, prev);
	}

	private static List<KpiTile> kpis(long[] cur, long[] prev, boolean hasPrev, List<TimingRosterRow> roster) {
		double curS = cur[0] / 1000.0;
		double dSec = hasPrev ? (cur[0] - prev[0]) / 1000.0 : 0;
		long dCount = hasPrev ? (cur[1] - prev[1]) : 0;
		List<KpiTile> tiles = new ArrayList<>();
		tiles.add(new KpiTile("Suite time", AnalyticsView.fmt1(curS) + "s", "",
				hasPrev ? (AnalyticsView.signed1(dSec) + "s") : null, AnalyticsView.upIsBad(dSec, 0.05), null));
		tiles.add(new KpiTile("Tests", Long.toString(cur[1]), "", hasPrev ? AnalyticsView.signedL(dCount) : null,
				"flat", null));
		if (!roster.isEmpty()) {
			TimingRosterRow top = roster.get(0);
			tiles.add(new KpiTile("Slowest test", fmtMs(top.durationMs()), "", null, "flat", top.displayName()));
		}
		return tiles;
	}

	/**
	 * Slowest tests in the latest run (module-scoped), each with a Δ vs the same test
	 * last run.
	 */
	private List<TimingRosterRow> roster(List<TestCaseResult> curCases, List<String> curMods, String module,
			TestRun prev) {
		Map<String, Long> prevDur = new HashMap<>();
		if (prev != null) {
			for (TestCaseResult c : reporting.allCasesFor(prev.getId())) {
				prevDur.put(key(c.getClassName(), c.getName()), c.getDurationMs());
			}
		}
		List<TimingRosterRow> rows = new ArrayList<>();
		for (int i = 0; i < curCases.size(); i++) {
			if (module != null && !module.equals(curMods.get(i))) {
				continue;
			}
			TestCaseResult c = curCases.get(i);
			Long was = prevDur.get(key(c.getClassName(), c.getName()));
			Long delta = (was != null) ? (c.getDurationMs() - was) : null;
			rows.add(new TimingRosterRow(c.getSuiteName(), c.getClassName(), c.getName(), c.getStatus().name(),
					c.getDurationMs(), delta));
		}
		rows.sort(Comparator.comparingLong(TimingRosterRow::durationMs).reversed());
		return (rows.size() > SLOW_LIMIT) ? rows.subList(0, SLOW_LIMIT) : rows;
	}

	/** Suite-time (seconds, left axis) + test-count (right axis) trend, module-scoped. */
	private TrendView trend(List<TestRun> trend, boolean scoped, String module) {
		List<Long> runIds = trend.stream().map(TestRun::getId).toList();
		List<Long> seconds;
		List<Long> tests;
		if (scoped) {
			List<long[]> mt = reporting.moduleTimingTrend(runIds, module);
			seconds = mt.stream().map((a) -> Math.round(a[0] / 1000.0)).toList();
			tests = mt.stream().map((a) -> a[1]).toList();
		}
		else {
			seconds = trend.stream().map((r) -> Math.round(r.getDurationMs() / 1000.0)).toList();
			tests = trend.stream().map((r) -> (long) r.getTotalTests()).toList();
		}
		String cfg = AnalyticsView.trendConfig(AnalyticsView.trendLabelsFrom(trend), runIds,
				trend.stream().map(AnalyticsView::epochMilli).toList(),
				List.of(AnalyticsView.series("Suite time (s)", "#d29922", seconds),
						AnalyticsView.series("Tests", "#58a6ff", tests, "y2")),
				null, "seconds", "tests");
		String subtitle = (scoped) ? ("(" + module + " — suite time & test count per run)")
				: "(suite time & test count per run)";
		// A single point is not a trend — gate on >=2 runs so a first run shows the hint,
		// not a dot.
		return new TrendView(trend.size() >= 2, "Suite-time trend", subtitle, cfg);
	}

	/** Timing by module with a Δ-total-time column vs the previous run. */
	private BreakdownTable breakdown(Long id, String flag, TestRun cur, TestRun prev, String selected) {
		Map<String, Long> prevTotal = new HashMap<>();
		if (prev != null) {
			for (TestModuleTiming m : reporting.testModuleTiming(prev.getId())) {
				prevTotal.put(m.name(), m.totalMs());
			}
		}
		List<BreakdownRow> rows = new ArrayList<>();
		for (TestModuleTiming m : reporting.testModuleTiming(cur.getId())) {
			Long was = prevTotal.get(m.name());
			// Δ total time vs the previous run — coloured like the roster Δ (slower =
			// down/red).
			BreakdownCell deltaCell = (was != null)
					? new BreakdownCell(AnalyticsView.signed1((m.totalMs() - was) / 1000.0) + "s",
							"delta " + AnalyticsView.upIsBad((m.totalMs() - was) / 1000.0, 0.05))
					: BreakdownCell.of("new");
			rows.add(new BreakdownRow(m.name(), AnalyticsView.moduleUrl("performance", id, flag, m.name()), false,
					List.of(BreakdownCell.of(String.valueOf(m.tests())),
							BreakdownCell.of(AnalyticsView.fmt1(m.totalMs() / 1000.0) + "s"),
							BreakdownCell.of(fmtMs(m.avgMs())), deltaCell)));
		}
		return AnalyticsView.moduleBreakdown(selected, rows, "Timing by module",
				List.of("Module", "Tests", "Total", "Avg", "Δ total"));
	}

	private static String validModule(String module, List<String> mods) {
		return (module != null && mods.contains(module)) ? module : null;
	}

	private static String key(String className, String name) {
		return ((className != null) ? className : "") + "#" + name;
	}

	private static String fmtMs(long ms) {
		return String.format(java.util.Locale.US, "%,d ms", ms);
	}

}
