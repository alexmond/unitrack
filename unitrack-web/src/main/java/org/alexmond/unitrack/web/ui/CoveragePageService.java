package org.alexmond.unitrack.web.ui;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.alexmond.unitrack.domain.CoverageReport;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.ModuleCoverage;
import org.alexmond.unitrack.report.ReportingService;
import org.alexmond.unitrack.web.ui.view.BreakdownRow;
import org.alexmond.unitrack.web.ui.view.BreakdownTable;
import org.alexmond.unitrack.web.ui.view.CoveragePage;
import org.alexmond.unitrack.web.ui.view.EmptyState;
import org.alexmond.unitrack.web.ui.view.KpiTile;
import org.alexmond.unitrack.web.ui.view.TrendView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the {@link CoveragePage} on the shared analytics skeleton (mirroring
 * Tests/Timing): KPI tiles (line +Δ, branch, instruction, method), a line-coverage trend,
 * a by-module breakdown that scopes the tab, and the by-package + worst-covered-files
 * tables (module-scoped).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
class CoveragePageService {

	private static final String TREND_FLAG = "default";

	private static final int TREND_LIMIT = 30;

	private static final int FILE_LIMIT = 200;

	private static final EmptyState EMPTY = new EmptyState("bi-shield-slash", "No coverage yet",
			"Attach a JaCoCo, Cobertura, LCOV or OpenCover report to an ingest and per-file coverage shows up here.");

	private final ReportingService reporting;

	CoveragePage build(Project project, Long id, String module) {
		String all = "/projects/" + id + "/coverage";
		CoverageReport c = reporting.latestCoverage(id).orElse(null);
		if (c == null) {
			return new CoveragePage(project, false, null, all, false, List.of(), null,
					new TrendView(false, "Line-coverage trend", null, "{}"), null, EMPTY, List.of(), List.of());
		}
		List<ModuleCoverage> modules = reporting.moduleCoverage(c.getId());
		String selected = validModule(module, modules);
		List<TestRun> trend = reporting.trendRuns(id, null, TREND_FLAG, TREND_LIMIT);
		return new CoveragePage(project, selected != null, selected, all, true, kpis(c, lineDelta(trend)),
				AnalyticsView.latestRunLine(c.getRun()), trend(trend), breakdown(id, modules, selected), EMPTY,
				reporting.coveragePackages(c.getId(), selected),
				reporting.coverageFiles(c.getId(), selected, FILE_LIMIT));
	}

	private static List<KpiTile> kpis(CoverageReport c, Double lineDelta) {
		KpiTile line = new KpiTile("Line", AnalyticsView.fmt1(c.getLinePct()) + "%",
				AnalyticsView.coverageLevel(c.getLinePct()),
				(lineDelta != null) ? (AnalyticsView.signed1(lineDelta) + " pp") : null,
				AnalyticsView.upIsGood((lineDelta != null) ? lineDelta : 0, 0.05), null);
		return List.of(line, kpi("Branch", c.getBranchPct()), kpi("Instruction", c.getInstructionPct()),
				kpi("Method", c.getMethodPct()));
	}

	private static KpiTile kpi(String label, double pct) {
		return KpiTile.of(label, AnalyticsView.fmt1(pct) + "%", AnalyticsView.coverageLevel(pct));
	}

	private TrendView trend(List<TestRun> trend) {
		List<Double> line = trend.stream().map(TestRun::getLineCoveragePct).toList();
		String cfg = AnalyticsView.trendConfig(AnalyticsView.trendLabelsFrom(trend),
				trend.stream().map(TestRun::getId).toList(), trend.stream().map(AnalyticsView::epochMilli).toList(),
				List.of(AnalyticsView.series("Line coverage %", "#4493f8", line)), null, "% line", null, 0, 100);
		return new TrendView(!trend.isEmpty(), "Line-coverage trend", null, cfg);
	}

	/**
	 * Coverage by module (null for single-module projects); clicking a row scopes the
	 * tab.
	 */
	private static BreakdownTable breakdown(Long id, List<ModuleCoverage> modules, String selected) {
		if (modules.size() <= 1) {
			return null;
		}
		List<BreakdownRow> rows = modules.stream()
			.map((m) -> new BreakdownRow(m.name(), "/projects/" + id + "/coverage?module=" + enc(m.name()), false,
					List.of(AnalyticsView.fmt1(m.linePct()) + "%", m.lineCovered() + "/" + m.lineTotal(),
							String.valueOf(m.files()))))
			.toList();
		return AnalyticsView.moduleBreakdown(selected, rows, "Coverage by module",
				List.of("Module", "Line %", "Lines", "Files"));
	}

	/**
	 * Change in line coverage between the latest report carrying it and the previous one,
	 * or null.
	 */
	private static Double lineDelta(List<TestRun> trendOldestFirst) {
		Double cur = null;
		Double prev = null;
		for (TestRun r : trendOldestFirst) {
			if (r.getLineCoveragePct() != null) {
				prev = cur;
				cur = r.getLineCoveragePct();
			}
		}
		return (cur != null && prev != null) ? cur - prev : null;
	}

	private static String validModule(String module, List<ModuleCoverage> modules) {
		boolean known = module != null && modules.stream().anyMatch((m) -> m.name().equals(module));
		return known ? module : null;
	}

	private static String enc(String v) {
		return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8);
	}

}
