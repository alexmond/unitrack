package org.alexmond.unitrack.web.ui.view;

/**
 * The primary trend chart for an analytics tab. {@link #configJson()} is the
 * fully-assembled argument for {@code window.__trendChart(canvasId, toggleId, cfg)}
 * (labels, runIds, times, series, overlaySeries, axis titles) — the page emits it
 * verbatim, so the chart's data shape lives in one place. A single-run tab has
 * {@code present=false}: the fragment renders nothing and the page shows a "first run"
 * hint instead.
 *
 * @param present whether there is enough history to draw a trend (≥1 run with data)
 * @param title the section heading
 * @param subtitle a muted qualifier (e.g. "(pass/fail per run)"), or null
 * @param configJson the JSON config passed to {@code window.__trendChart}
 */
public record TrendView(boolean present, String title, String subtitle, String configJson) {
}
