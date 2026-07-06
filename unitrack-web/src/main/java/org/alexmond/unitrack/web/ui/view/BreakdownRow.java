package org.alexmond.unitrack.web.ui.view;

import java.util.List;

/**
 * One row of a {@link BreakdownTable}: a named group (module / transaction) that scopes
 * the whole tab when clicked, plus its formatted numeric cells (aligned to
 * {@code BreakdownTable.columns()} after the first, name column).
 *
 * @param name the group name (rendered as the blue mono link)
 * @param href where clicking the row/name navigates (the tab scoped to this group)
 * @param failing whether to tint the row as failing
 * @param cells the formatted cells for the non-name columns, in column order
 */
public record BreakdownRow(String name, String href, boolean failing, List<BreakdownCell> cells) {
}
