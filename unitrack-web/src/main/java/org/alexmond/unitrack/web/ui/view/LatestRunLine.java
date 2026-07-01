package org.alexmond.unitrack.web.ui.view;

/**
 * The "Latest run &lt;sha&gt; on &lt;branch&gt; · &lt;when&gt;" line shown under the KPI
 * tiles on every analytics tab, linking to the run (L4). Rendered by
 * {@code fragments/analytics :: latestRunLine}; a null instance renders nothing.
 *
 * @param runId the run to link to
 * @param label the run label (short SHA, or "#id")
 * @param branch the run's branch, or null
 * @param whenText the formatted timestamp
 */
public record LatestRunLine(Long runId, String label, String branch, String whenText) {
}
