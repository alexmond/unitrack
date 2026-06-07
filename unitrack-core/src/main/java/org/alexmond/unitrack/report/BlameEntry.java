package org.alexmond.unitrack.report;

/**
 * For a currently-failing test, the run/commit where its current failing streak began on
 * the same branch -- i.e. the first run that failed since the test was last green. Helps
 * answer "which commit broke this?".
 */
public record BlameEntry(String className, String name, Long firstFailingRunId, String firstFailingCommit,
		String firstFailingShortSha) {
}
