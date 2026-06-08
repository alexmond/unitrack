package org.alexmond.unitrack.report;

import java.time.Instant;
import java.util.List;

/** A single perf run with its per-label breakdown, baseline deltas, and gate verdict. */
public record PerfRunDetail(Long runId, Long projectId, String projectName, String commitSha, String branch,
		String format, Instant createdAt, double p50Ms, double p90Ms, double p95Ms, double p99Ms, double throughputRps,
		double errorPct, long sampleCount, long durationMs, PerfRunRegression regression, List<LabelRow> labels) {

	/**
	 * One request label, with its p95 delta vs the same label in the baseline run (if
	 * any).
	 */
	public record LabelRow(String label, long sampleCount, double errorPct, double p50Ms, double p90Ms, double p95Ms,
			double p99Ms, Double baselineP95Ms, Double p95DeltaPct) {
	}
}
