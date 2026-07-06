package org.alexmond.unitrack.web.ui.view;

/**
 * One formatted cell of a {@link BreakdownRow}. The optional CSS class lets a cell carry
 * a direction colour (e.g. {@code "delta down"} for a slower module) so a breakdown Δ
 * reads the same as a roster Δ — a plain value uses {@link #of(String)} with no class.
 *
 * @param text the formatted value
 * @param cls extra CSS class(es) for the cell, or "" for none
 */
public record BreakdownCell(String text, String cls) {

	/** A plain cell with no extra styling. */
	public static BreakdownCell of(String text) {
		return new BreakdownCell(text, "");
	}

}
