package org.alexmond.unitrack.report;

import java.util.List;

/**
 * Verdict for a performance/load-test run vs its baseline: whether p95 latency,
 * throughput and error rate stayed within the configured thresholds.
 */
public record PerfRunRegression(boolean baselineFound, Long baselineRunId, String baseBranch, boolean passed,
		List<Rule> rules) {

	public boolean regressed() {
		return !this.passed;
	}

	public String status() {
		return this.passed ? "PASSED" : "REGRESSED";
	}

	/** Result of a single perf-gate rule. */
	public record Rule(String name, boolean passed, String detail) {
	}
}
