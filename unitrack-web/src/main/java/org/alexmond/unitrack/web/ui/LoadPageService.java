package org.alexmond.unitrack.web.ui;

import java.util.List;
import java.util.Locale;

import lombok.RequiredArgsConstructor;

import org.alexmond.unitrack.domain.PerfRun;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.report.PerfRunDetail;
import org.alexmond.unitrack.report.PerfRunDetailService;
import org.alexmond.unitrack.report.PerfStepDetectionService;
import org.alexmond.unitrack.report.PerfStepSignal;
import org.alexmond.unitrack.report.PerfTrendPoint;
import org.alexmond.unitrack.report.ReportingService;
import org.alexmond.unitrack.web.ui.view.EmptyState;
import org.alexmond.unitrack.web.ui.view.KpiTile;
import org.alexmond.unitrack.web.ui.view.LoadPage;
import org.alexmond.unitrack.web.ui.view.ScopeBar;
import org.alexmond.unitrack.web.ui.view.TrendView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the {@link LoadPage} reusing the shared analytics pieces (KPI tiles, empty
 * state, the trend chart via {@code trend.js}) while keeping Load tests' own shape:
 * perf-flag scope, three charts (latency / throughput / error), a p95-regression banner,
 * and a recent-runs table. Chart points link to the perf-run detail (not Compare).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
class LoadPageService {

	private static final String TREND_FLAG = "default";

	private static final int TREND_LIMIT = 30;

	private static final int RUN_LIST_LIMIT = 50;

	private static final EmptyState EMPTY = new EmptyState("bi-rocket-takeoff", "No load tests yet",
			"Upload a JMeter JTL or k6 JSON summary with an ingest (--perf results.jtl) to chart latency, "
					+ "throughput and errors.");

	private final ReportingService reporting;

	private final PerfStepDetectionService perfStepDetection;

	private final PerfRunDetailService perfRunDetail;

	LoadPage build(Project project, Long id, String flag) {
		List<String> flags = reporting.perfFlags(id);
		String selectedFlag = selectedPerfFlag(flag, flags);
		List<PerfTrendPoint> trend = reporting.perfTrend(id, selectedFlag, TREND_LIMIT);
		List<PerfRun> perfRuns = reporting.recentPerfRuns(id, selectedFlag, RUN_LIST_LIMIT);
		PerfStepSignal step = perfStepDetection.detectLatencyStep(id, selectedFlag).orElse(null);
		String base = AnalyticsView.repoBase(project.getRepoUrl());
		String repoCommitBase = (base != null) ? (base + "/commit/") : null;
		// By-transaction breakdown = the latest run's per-label rows (with p95 Δ vs
		// baseline).
		List<PerfRunDetail.LabelRow> transactions = perfRuns.isEmpty() ? List.of()
				: perfRunDetail.detail(perfRuns.get(0).getId()).map(PerfRunDetail::labels).orElse(List.of());
		// Branch scoping isn't wired for Load yet (perf queries take no branch) — flag
		// only. (#431)
		ScopeBar scope = new ScopeBar("/projects/" + id + "/perf", flags, selectedFlag, null, null, null);
		return new LoadPage(project, !trend.isEmpty(), kpis(perfRuns), EMPTY, latency(trend), throughput(trend),
				error(trend), step, perfRuns, flags, selectedFlag, repoCommitBase, transactions, scope);
	}

	private static List<KpiTile> kpis(List<PerfRun> perfRuns) {
		if (perfRuns.isEmpty()) {
			return List.of();
		}
		PerfRun cur = perfRuns.get(0);
		PerfRun prev = (perfRuns.size() > 1) ? perfRuns.get(1) : null;
		boolean hp = prev != null;
		double dP95 = hp ? (cur.getP95Ms() - prev.getP95Ms()) : 0;
		double dTp = hp ? (cur.getThroughputRps() - prev.getThroughputRps()) : 0;
		double dErr = hp ? (cur.getErrorPct() - prev.getErrorPct()) : 0;
		return List.of(
				new KpiTile("p95 latency", f0(cur.getP95Ms()) + " ms", "", hp ? (sgn0(dP95) + " ms") : null,
						AnalyticsView.upIsBad(dP95, 0.5), null),
				new KpiTile("Throughput", f1(cur.getThroughputRps()) + " rps", "", hp ? (sgn1(dTp) + " rps") : null,
						AnalyticsView.upIsGood(dTp, 0.05), null),
				new KpiTile("Error rate", f2(cur.getErrorPct()) + "%", (cur.getErrorPct() > 0) ? "lvl-bad" : "lvl-good",
						hp ? (sgn2(dErr) + " pp") : null, AnalyticsView.upIsBad(dErr, 0.005), null),
				KpiTile.of("Samples", String.format(Locale.US, "%,d", cur.getSampleCount()), ""));
	}

	private static TrendView latency(List<PerfTrendPoint> t) {
		// p95 is the headline metric (KPI tile + regression banner), so it's plotted here
		// too —
		// otherwise the one line the alert is about wouldn't appear on its own chart.
		String cfg = AnalyticsView.perfTrendConfig(labels(t), ids(t), times(t),
				List.of(AnalyticsView.series("p50", "#2ea043", t.stream().map(PerfTrendPoint::p50Ms).toList()),
						AnalyticsView.series("p90", "#d29922", t.stream().map(PerfTrendPoint::p90Ms).toList()),
						AnalyticsView.series("p95", "#58a6ff", t.stream().map(PerfTrendPoint::p95Ms).toList()),
						AnalyticsView.series("p99", "#f85149", t.stream().map(PerfTrendPoint::p99Ms).toList())),
				"ms");
		// A single point is not a trend — gate on >=2 runs so a first run shows the hint,
		// not a dot.
		return new TrendView(t.size() >= 2, "Latency", "(p50 / p90 / p95 / p99, ms)", cfg);
	}

	private static TrendView throughput(List<PerfTrendPoint> t) {
		String cfg = AnalyticsView.perfTrendConfig(labels(t), ids(t), times(t), List
			.of(AnalyticsView.series("req/s", "#4493f8", t.stream().map(PerfTrendPoint::throughputRps).toList())),
				"req/s");
		return new TrendView(!t.isEmpty(), "Throughput", "(req/s)", cfg);
	}

	private static TrendView error(List<PerfTrendPoint> t) {
		String cfg = AnalyticsView.perfTrendConfig(labels(t), ids(t), times(t),
				List.of(AnalyticsView.series("error %", "#f85149", t.stream().map(PerfTrendPoint::errorPct).toList())),
				"%");
		return new TrendView(!t.isEmpty(), "Error rate", "(%)", cfg);
	}

	private static List<String> labels(List<PerfTrendPoint> t) {
		return AnalyticsView.labels(t.stream().map(PerfTrendPoint::shortSha).toList());
	}

	private static List<Long> ids(List<PerfTrendPoint> t) {
		return t.stream().map(PerfTrendPoint::runId).toList();
	}

	private static List<Long> times(List<PerfTrendPoint> t) {
		return t.stream().map((p) -> (p.createdAt() != null) ? p.createdAt().toEpochMilli() : null).toList();
	}

	/**
	 * The perf series to show: the requested flag if valid, else the default rollup, else
	 * first.
	 */
	private static String selectedPerfFlag(String requested, List<String> flags) {
		if (requested != null && !requested.isBlank() && flags.contains(requested)) {
			return requested;
		}
		if (flags.contains(TREND_FLAG)) {
			return TREND_FLAG;
		}
		return flags.isEmpty() ? null : flags.get(0);
	}

	private static String f0(double v) {
		return String.format(Locale.US, "%,.0f", v);
	}

	private static String f1(double v) {
		return String.format(Locale.US, "%,.1f", v);
	}

	private static String f2(double v) {
		return String.format(Locale.US, "%,.2f", v);
	}

	private static String sgn0(double v) {
		return ((v >= 0) ? "+" : "") + f0(v);
	}

	private static String sgn1(double v) {
		return ((v >= 0) ? "+" : "") + f1(v);
	}

	private static String sgn2(double v) {
		return ((v >= 0) ? "+" : "") + f2(v);
	}

}
