package org.alexmond.unitrack.report;

import java.time.Instant;

import org.alexmond.unitrack.domain.FlakyStatus;

/** A detected flaky test combined with its user-controlled {@link FlakyStatus}. */
public record FlakyTestView(String className, String name, long flakyCommits, long totalResults, long failures,
		double failureRatePct, Instant lastFailureAt, FlakyStatus status, String note) {

	public String displayName() {
		return (className == null || className.isBlank()) ? name : className + "#" + name;
	}

}
