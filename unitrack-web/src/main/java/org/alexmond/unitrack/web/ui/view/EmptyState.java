package org.alexmond.unitrack.web.ui.view;

/**
 * The empty-state shown when an analytics tab has no run yet. Rendered by
 * {@code fragments/analytics :: emptyState}.
 *
 * @param icon a Bootstrap icon class (e.g. "bi-check2-square")
 * @param title the heading
 * @param message the explanatory line
 */
public record EmptyState(String icon, String title, String message) {
}
