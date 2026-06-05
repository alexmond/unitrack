package org.alexmond.unitrack.report;

import java.time.LocalDateTime;

/**
 * Native-query projection for a detected flaky test: a test that, for at least one
 * commit, was observed both passing and failing.
 */
public interface FlakyStat {

	String getClassName();

	String getName();

	/** Number of commits where the test both passed and failed. */
	long getFlakyCommits();

	/** Total recorded results for this test across all runs. */
	long getTotalResults();

	/** Number of failing/erroring results across all runs. */
	long getFailures();

	LocalDateTime getLastFailureAt();

}
