package org.alexmond.unitrack.web.ui;

import java.util.List;

import org.alexmond.unitrack.report.FailureCluster;

/**
 * The two folded Failure-clusters sections on the Tests tab: real {@code clusters} span
 * &gt;1 distinct test; {@code recurring} are single tests failing repeatedly (excluding
 * ones already shown in the Flaky section).
 */
record ClusterSections(List<FailureCluster> clusters, List<FailureCluster> recurring) {
}
