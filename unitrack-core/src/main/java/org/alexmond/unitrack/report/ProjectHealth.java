package org.alexmond.unitrack.report;

import java.time.Instant;

import org.alexmond.unitrack.domain.Visibility;

/**
 * One project's health for the global board: gate status, pass%, coverage%, flaky count
 * and the pass-rate trend direction of its latest run. {@code trend} is +1 up / -1 down /
 * 0 flat (or fewer than two runs); {@code gateStatus} and the rates are null when the
 * project has no runs.
 */
public record ProjectHealth(Long projectId, String projectName, Long lastRunId, Instant lastRunAt, String branch,
		String gateStatus, Double passRate, Double coveragePct, long flakyCount, int trend, Visibility visibility) {

	public boolean hasRuns() {
		return this.lastRunId != null;
	}

	public boolean gatePassed() {
		return "PASSED".equals(this.gateStatus);
	}

	public boolean isPrivate() {
		return this.visibility == Visibility.PRIVATE;
	}

}
