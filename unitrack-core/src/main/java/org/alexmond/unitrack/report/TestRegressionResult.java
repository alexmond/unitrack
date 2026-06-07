package org.alexmond.unitrack.report;

import java.util.List;

/**
 * Diff of a run's test outcomes against its baseline run on the base branch: which tests
 * newly fail, which newly pass (got fixed), and which keep failing.
 */
public record TestRegressionResult(boolean baselineFound, Long baselineRunId, String baseBranch,
		List<RegressedTest> newFailures, List<RegressedTest> newPasses, List<RegressedTest> stillFailing) {

	public int newFailureCount() {
		return this.newFailures.size();
	}

	public int newPassCount() {
		return this.newPasses.size();
	}

	public int stillFailingCount() {
		return this.stillFailing.size();
	}

	/** A regression (new failures vs the baseline) is the headline signal. */
	public boolean hasRegressions() {
		return !this.newFailures.isEmpty();
	}

	/**
	 * True when there is nothing to report: no new failures, passes, or carried-over
	 * failures.
	 */
	public boolean empty() {
		return this.newFailures.isEmpty() && this.newPasses.isEmpty() && this.stillFailing.isEmpty();
	}

	/**
	 * A single test in the diff; failure type/message are populated only for failing
	 * tests.
	 */
	public record RegressedTest(String className, String name, String failureType, String failureMessage) {
	}
}
