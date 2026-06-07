package org.alexmond.unitrack.report;

import org.alexmond.unitrack.domain.TestCaseResult;

/** A single slow test case, for the slowest-tests leaderboard. */
public record SlowTest(String suiteName, String className, String name, String status, long durationMs) {

	public static SlowTest of(TestCaseResult c) {
		return new SlowTest(c.getSuiteName(), c.getClassName(), c.getName(), c.getStatus().name(), c.getDurationMs());
	}
}
