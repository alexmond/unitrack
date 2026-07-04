package org.alexmond.unitrack.web.ui.view;

import java.util.List;

import org.alexmond.unitrack.domain.Project;

/**
 * The reconciled project Overview (preview): a computed health verdict, then four
 * one-signal aspect cards that route into their tabs, then the multi-series "Health over
 * time" trend. It synthesizes the four aspect tabs rather than duplicating any of them
 * (see the 2026-07-04 brainstorm panel). The demoted tables (recent runs, branches, PRs)
 * are passed as separate model attributes, mirroring the classic Overview.
 *
 * @param project the project
 * @param tab the subnav key ({@code "overview"})
 * @param hasRuns whether the project has any runs (drives the empty state)
 * @param verdictWord the one-word health verdict
 * ({@code Healthy|At risk|Failing|No runs})
 * @param verdictLevel the verdict colour class ({@code lvl-good|lvl-warn|lvl-bad})
 * @param verdictLine the single worst finding, in words
 * @param chips deep-link chips for the deciding conditions (may be empty)
 * @param passRate latest-run pass rate (pass-of-executed) for the donut centre label
 * @param donutPass latest-run pass-of-total for the donut's green arc (so the amber
 * skipped arc renders correctly; all three arcs are on the total basis)
 * @param failPct latest-run failure percentage (of total) for the donut's red arc
 * @param heroStatus gate verdict status for the hero pill (falls back to run status when
 * no gate is configured), so the pill agrees with the verdict word
 * @param latestRun the latest-run line ({@code null} when there are no runs)
 * @param cards the aspect strip, ordered trouble-first (Load omitted when absent)
 * @param trend the "Health over time" multi-series trend
 */
public record OverviewPage(Project project, String tab, boolean hasRuns, String verdictWord, String verdictLevel,
		String verdictLine, List<VerdictChip> chips, double passRate, double donutPass, double failPct,
		String heroStatus, LatestRunLine latestRun, List<AspectCard> cards, TrendView trend) {

	/**
	 * A verdict-band chip naming one deciding condition and linking to the tab that owns
	 * it.
	 *
	 * @param text the condition, in words (e.g. {@code "Coverage 78% < 80%"})
	 * @param href the tab to route to
	 * @param level a colour class ({@code lvl-warn|lvl-bad})
	 */
	public record VerdictChip(String text, String href, String level) {
	}
}
