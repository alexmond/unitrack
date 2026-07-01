package org.alexmond.unitrack.web.ui.view;

import java.util.List;

import org.alexmond.unitrack.domain.PerfRun;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.report.PerfRunDetail;
import org.alexmond.unitrack.report.PerfStepSignal;

/**
 * The Load-tests tab as one model object (the single {@code page} attribute), on the
 * shared {@link AnalyticsPage} skeleton — KPI tiles + empty state + primary trend come
 * from the shared fragments. Load tests differ from the module tabs: they scope by perf
 * <em>flag</em> (a series, not a module), show three charts (latency / throughput /
 * error), a p95-regression banner, and a recent-runs table instead of a roster — so those
 * are Load-specific accessors. The primary {@link #trend()} is the latency chart;
 * {@link #breakdown()} is null (no by-module breakdown).
 *
 * @param project the project (crumbs/title)
 * @param hasRun whether there is any perf data on the selected series
 * @param kpis the KPI tiles (p95 +Δ, throughput +Δ, error rate +Δ, samples)
 * @param empty the empty-state
 * @param trend the latency trend (p50/p90/p99) — the shared primary trend
 * @param throughputTrend the throughput chart
 * @param errorTrend the error-rate chart
 * @param regression the detected p95 step (banner), or null
 * @param perfRuns the recent perf runs (table → perf-run detail)
 * @param flags the perf flags/series (scope control)
 * @param selectedFlag the currently-shown series
 * @param repoCommitBase GitHub-style commit base ({@code <repo>/commit/}) for linking a
 * perf-run's commit SHA, or null when the project has no repo URL
 * @param transactions the latest run's per-transaction/label rows (the by-transaction
 * breakdown, with a p95 Δ vs baseline so you can see which label regressed)
 */
public record LoadPage(Project project, boolean hasRun, List<KpiTile> kpis, EmptyState empty, TrendView trend,
		TrendView throughputTrend, TrendView errorTrend, PerfStepSignal regression, List<PerfRun> perfRuns,
		List<String> flags, String selectedFlag, String repoCommitBase, List<PerfRunDetail.LabelRow> transactions,
		ScopeBar scope) implements AnalyticsPage {

	@Override
	public String tab() {
		return "load";
	}

	@Override
	public boolean scoped() {
		return false;
	}

	@Override
	public String selectedModule() {
		return null;
	}

	@Override
	public String allUrl() {
		return null;
	}

	@Override
	public LatestRunLine latestRun() {
		return null;
	}

	@Override
	public BreakdownTable breakdown() {
		return null;
	}

}
