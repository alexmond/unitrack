package org.alexmond.unitrack.report;

import java.time.Instant;

/**
 * A detected sustained step (regression) in a perf series: when it started and how deep
 * it is relative to the series' own noise (#379).
 *
 * @param onsetCommit commit of the run where the step began (may be null)
 * @param onsetAt timestamp of that run
 * @param depthZ depth of the shift as a robust z-score
 * @param baselineMedian the prior stable level (ms)
 * @param recentMedian the current level (ms)
 */
public record PerfStepSignal(String onsetCommit, Instant onsetAt, double depthZ, double baselineMedian,
		double recentMedian) {
}
