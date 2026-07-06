package org.alexmond.unitrack.web.ui.view;

import java.util.List;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.report.FailureCluster;
import org.alexmond.unitrack.report.FlakyTestView;
import org.alexmond.unitrack.report.TestRosterRow;

/**
 * The whole Tests tab as one model object (the single {@code page} model attribute).
 * Carries the shared {@link AnalyticsPage} skeleton plus the Tests-specific sections: the
 * all-tests roster with its counts strip, and the folded-in Flaky + Failure-clusters
 * sections. Built by the controller so the template does no computation.
 *
 * @param project the project (crumbs/title)
 * @param scoped whether scoped to one module
 * @param selectedModule the scoped module, or null
 * @param allUrl the back-to-all-tests URL
 * @param hasRun whether there is a latest run
 * @param kpis the KPI tiles
 * @param latestRun the latest-run line, or null
 * @param trend the pass/fail trend
 * @param breakdown the by-module breakdown, or null
 * @param empty the empty-state
 * @param roster the all-tests roster (latest run, module-scoped)
 * @param failing count of failing tests in the roster's scope
 * @param flakyCount count of flaky tests in the roster
 * @param fixedCount count of red→green (fixed) tests in the roster
 * @param skipped count of skipped tests
 * @param passed count of passing tests
 * @param flaky the folded Flaky-tests section
 * @param aiEnabled whether the AI root-cause action is available
 * @param clusters the folded Failure-clusters (multi-test)
 * @param recurringFailures single-test recurring failures (not already flaky)
 */
public record TestsPage(Project project, boolean scoped, String selectedModule, String allUrl, boolean hasRun,
		List<KpiTile> kpis, LatestRunLine latestRun, TrendView trend, BreakdownTable breakdown, EmptyState empty,
		List<TestRosterRow> roster, long failing, long flakyCount, long fixedCount, long skipped, long passed,
		List<FlakyTestView> flaky, boolean aiEnabled, List<FailureCluster> clusters,
		List<FailureCluster> recurringFailures, ScopeBar scope) implements AnalyticsPage {

	@Override
	public String tab() {
		return "tests";
	}

	/** Total tests in the roster (the "All tests (N)" count + "Show all N" label). */
	public int rosterSize() {
		return roster.size();
	}

}
