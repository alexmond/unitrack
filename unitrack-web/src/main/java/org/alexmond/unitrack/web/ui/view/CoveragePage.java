package org.alexmond.unitrack.web.ui.view;

import java.util.List;

import org.alexmond.unitrack.domain.CoverageFileEntry;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.report.CoveragePackage;

/**
 * The whole Coverage tab as one model object (the single {@code page} attribute), on the
 * shared {@link AnalyticsPage} skeleton as Tests/Timing. Adds the coverage-specific
 * by-package and worst-covered-files tables, which the by-module breakdown scopes
 * ({@code ?module=}).
 *
 * @param project the project (crumbs/title)
 * @param scoped whether scoped to one module
 * @param selectedModule the scoped module, or null
 * @param allUrl the back-to-all-coverage URL
 * @param hasRun whether there is a coverage report
 * @param kpis the KPI tiles (line +Δ, branch, instruction, method)
 * @param latestRun the latest-report run line, or null
 * @param trend the line-coverage trend
 * @param breakdown the by-module breakdown, or null (single-module projects)
 * @param empty the empty-state
 * @param packages the by-package rows (module-scoped)
 * @param worstFiles the worst-covered files (module-scoped)
 */
public record CoveragePage(Project project, boolean scoped, String selectedModule, String allUrl, boolean hasRun,
		List<KpiTile> kpis, LatestRunLine latestRun, TrendView trend, BreakdownTable breakdown, EmptyState empty,
		List<CoveragePackage> packages, List<CoverageFileEntry> worstFiles) implements AnalyticsPage {

	@Override
	public String tab() {
		return "coverage";
	}

}
