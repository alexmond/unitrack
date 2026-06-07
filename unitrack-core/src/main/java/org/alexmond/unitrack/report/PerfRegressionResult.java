package org.alexmond.unitrack.report;

import java.util.List;

/**
 * Slow-test duration regression for a run vs its baseline: tests that ran significantly
 * slower than in the baseline run.
 */
public record PerfRegressionResult(boolean baselineFound, Long baselineRunId, String baseBranch,
		List<Slowdown> slower) {

	public int slowerCount() {
		return this.slower.size();
	}

	public boolean hasRegressions() {
		return !this.slower.isEmpty();
	}

	/** One test that got slower: its baseline and current durations and the delta. */
	public record Slowdown(String className, String name, long baselineMs, long currentMs, long deltaMs, double pct) {
	}
}
