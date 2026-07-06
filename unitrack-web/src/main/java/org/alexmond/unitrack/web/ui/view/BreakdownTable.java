package org.alexmond.unitrack.web.ui.view;

import java.util.List;

/**
 * The shared "by group" breakdown below the trend on an analytics tab (Tests by module,
 * Timing by module, Load by transaction). Rendered by
 * {@code fragments/analytics :: breakdown}; a null instance or empty {@code rows} renders
 * nothing (single-group projects hide it). Clicking a row scopes the whole tab to that
 * group; the selected row is highlighted.
 *
 * @param title the section title (e.g. "Tests by module")
 * @param subtitle a muted qualifier (e.g. "latest run"), or null
 * @param columns the column headers; the first is the group-name column, the rest are
 * numeric
 * @param rows the group rows
 * @param selected the currently-scoped group name (highlighted), or null
 */
public record BreakdownTable(String title, String subtitle, List<String> columns, List<BreakdownRow> rows,
		String selected) {
}
