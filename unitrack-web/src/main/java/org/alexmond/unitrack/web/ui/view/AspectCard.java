package org.alexmond.unitrack.web.ui.view;

/**
 * One aspect-summary card on the project Overview — a single decision-useful signal for
 * an aspect (Tests / Coverage / Test timing / Load) that routes into that aspect's tab
 * for depth. The whole card is a link to {@link #href()}; it never duplicates the tab.
 *
 * @param label the aspect name shown in the card header (e.g. {@code "Coverage"})
 * @param icon a Bootstrap icon class for the header (e.g. {@code "bi-shield-check"})
 * @param href the tab this card routes to (e.g. {@code /projects/1/coverage})
 * @param status a run/gate status for a {@code statusPill} ({@code null} = no pill)
 * @param value the hero number (e.g. {@code "84.1%"}, {@code "12.3s"}, {@code "210 ms"})
 * @param level a value colour class ({@code lvl-good|lvl-warn|lvl-bad} or {@code ""})
 * @param delta a signed change vs the previous run ({@code null} = first run / no prior)
 * @param deltaDir the delta direction class ({@code up|down|flat})
 * @param caption a one-line subline under the value (e.g. {@code "broken 5d"})
 */
public record AspectCard(String label, String icon, String href, String status, String value, String level,
		String delta, String deltaDir, String caption) {
}
