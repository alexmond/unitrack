package org.alexmond.unitrack.web.ui.view;

import java.util.List;

import org.alexmond.unitrack.domain.Project;

/**
 * The common shape of an analytics tab (Tests, Test timing, …). Every such page's model
 * object implements this so the shared {@code fragments/analytics} skeleton — scope chip
 * → empty state → KPI tiles → latest-run line → trend → by-group breakdown — renders from
 * one contract and can't drift between tabs. Concrete pages ({@code TestsPage},
 * {@code TimingPage}) add their own tab-specific sections (rosters, folded sections) as
 * extra accessors.
 */
public interface AnalyticsPage {

	/** The project this tab belongs to (crumbs, title, subnav). */
	Project project();

	/** The active subnav key ("tests", "timing", …). */
	String tab();

	/** Whether the tab is scoped to a single group (module). */
	boolean scoped();

	/** The scoped group name, or null when showing all. */
	String selectedModule();

	/** The "back to all" URL (the tab with no module scope). */
	String allUrl();

	/** Whether there is a latest run to show (false → render {@link #empty()}). */
	boolean hasRun();

	/** The KPI tile row. */
	List<KpiTile> kpis();

	/** The latest-run line, or null. */
	LatestRunLine latestRun();

	/** The primary trend chart. */
	TrendView trend();

	/** The by-group breakdown, or null when there are 0/1 groups. */
	BreakdownTable breakdown();

	/** The empty-state to show when {@link #hasRun()} is false. */
	EmptyState empty();

}
