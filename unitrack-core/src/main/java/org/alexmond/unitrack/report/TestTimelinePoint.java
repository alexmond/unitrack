package org.alexmond.unitrack.report;

import java.time.Instant;

import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.TestStatus;

/**
 * One point on a test's status/duration timeline: the status and duration in a single
 * run, with enough run metadata to build links and labels.
 */
public record TestTimelinePoint(Long runId, String shortSha, String branch, Instant createdAt, TestStatus status,
		long durationMs) {

	/**
	 * Maps a {@link TestCaseResult} (with its run already fetched) to a timeline point.
	 */
	public static TestTimelinePoint ofCase(TestCaseResult c) {
		TestRun run = c.getRun();
		return new TestTimelinePoint(run.getId(), run.getShortSha(), run.getBranch(), run.getCreatedAt(), c.getStatus(),
				c.getDurationMs());
	}

}
