package org.alexmond.unitrack.report;

import java.util.List;

/**
 * Project-level performance view: the total suite-time trend across recent runs and the
 * slowest tests in the latest run.
 */
public record PerformanceSummary(Long projectId, Long latestRunId, List<DurationPoint> suiteTimeTrend,
		List<SlowTest> slowestInLatestRun) {
}
