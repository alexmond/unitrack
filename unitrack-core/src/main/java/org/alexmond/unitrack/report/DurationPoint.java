package org.alexmond.unitrack.report;

import java.time.Instant;

import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;

/**
 * One point on a duration trend: a run's total suite time, or a single test's duration in
 * that run.
 */
public record DurationPoint(Long runId, String shortSha, Instant createdAt, long durationMs) {

	/** Total suite time for a run. */
	public static DurationPoint ofRun(TestRun run) {
		return new DurationPoint(run.getId(), run.getShortSha(), run.getCreatedAt(), run.getDurationMs());
	}

	/** A single test's duration in its run. */
	public static DurationPoint ofCase(TestCaseResult c) {
		TestRun run = c.getRun();
		return new DurationPoint(run.getId(), run.getShortSha(), run.getCreatedAt(), c.getDurationMs());
	}
}
