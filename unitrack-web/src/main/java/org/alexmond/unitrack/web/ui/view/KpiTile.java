package org.alexmond.unitrack.web.ui.view;

/**
 * One KPI tile in the shared analytics tile row: a headline value with an optional
 * threshold level, a change-vs-previous-run delta, and an optional secondary line.
 * Rendered by {@code fragments/analytics :: kpiRow}. The controller does the formatting
 * so every tab reads the same.
 *
 * @param label the tile caption (e.g. "Pass rate")
 * @param value the formatted headline value (e.g. "98.5%")
 * @param level a value CSS class for threshold colour ("lvl-good" | "lvl-warn" |
 * "lvl-bad" | "")
 * @param delta the formatted change vs the previous run (e.g. "+0.5 pp"), or null to
 * suppress
 * @param deltaDir delta direction class ("up" | "down" | "flat"), used when {@code delta}
 * is set
 * @param sub an optional muted secondary line (e.g. the slowest test's name), or null
 */
public record KpiTile(String label, String value, String level, String delta, String deltaDir, String sub) {

	/** A tile with a delta and no secondary line. */
	public static KpiTile of(String label, String value, String level, String delta, String deltaDir) {
		return new KpiTile(label, value, level, delta, deltaDir, null);
	}

	/** A tile with neither delta nor secondary line. */
	public static KpiTile of(String label, String value, String level) {
		return new KpiTile(label, value, level, null, "flat", null);
	}

}
