package org.alexmond.unitrack.ingest;

import java.util.List;

/**
 * Parsed performance-test run: aggregate latency percentiles, throughput and error rate,
 * plus per-request-label breakdown.
 */
public record PerfResults(long sampleCount, long errorCount, double errorPct, double throughputRps, long durationMs,
		double meanMs, double p50Ms, double p90Ms, double p95Ms, double p99Ms, double minMs, double maxMs,
		List<LabelStats> labels) {

	/** Aggregated metrics for a single request label. */
	public record LabelStats(String label, long sampleCount, long errorCount, double errorPct, double meanMs,
			double p50Ms, double p90Ms, double p95Ms, double p99Ms) {
	}
}
