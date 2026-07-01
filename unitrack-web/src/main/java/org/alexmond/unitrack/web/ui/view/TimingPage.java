package org.alexmond.unitrack.web.ui.view;

import java.util.List;

import org.alexmond.unitrack.domain.Project;

/**
 * The whole Test-timing tab as one model object (the single {@code page} attribute), on
 * the same {@link AnalyticsPage} skeleton as Tests. Adds the timing-specific
 * slowest-tests roster (with a Δ-vs-previous-run column). The trend carries two series —
 * suite time (seconds) and test count (on a second axis) — since growth in test count is
 * a prime cause of suite-time changes.
 *
 * @param project the project (crumbs/title)
 * @param scoped whether scoped to one module
 * @param selectedModule the scoped module, or null
 * @param allUrl the back-to-all-timing URL
 * @param hasRun whether there is timing data
 * @param kpis the KPI tiles
 * @param latestRun the latest-run line, or null
 * @param trend the suite-time + test-count trend
 * @param breakdown the by-module timing breakdown (with a Δ-time column), or null
 * @param empty the empty-state
 * @param slowest the slowest-tests roster (module-scoped), each with a Δ-vs-previous-run
 */
public record TimingPage(Project project, boolean scoped, String selectedModule, String allUrl, boolean hasRun,
		List<KpiTile> kpis, LatestRunLine latestRun, TrendView trend, BreakdownTable breakdown, EmptyState empty,
		List<TimingRosterRow> slowest) implements AnalyticsPage {

	@Override
	public String tab() {
		return "timing";
	}

	/** Number of tests in the slowest roster (the "Slowest tests (N)" count). */
	public int rosterSize() {
		return slowest.size();
	}

}
