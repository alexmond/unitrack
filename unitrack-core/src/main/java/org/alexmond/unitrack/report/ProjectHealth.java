package org.alexmond.unitrack.report;

import java.time.Instant;

import org.alexmond.unitrack.domain.Visibility;

/**
 * One project's health for the global board: gate status, pass%, coverage%, flaky count
 * and the pass-rate trend direction of its latest run. {@code trend} is +1 up / -1 down /
 * 0 flat (or fewer than two runs); {@code gateStatus} and the rates are null when the
 * project has no runs.
 *
 * <p>
 * When the latest run is failing, {@code brokenSince}/{@code daysRed}/{@code runsRed}
 * describe how long it has been red (the regression's age and the builds shipped on top
 * of it); {@code brokenSince} is null when the project is not currently regressed.
 */
public record ProjectHealth(Long projectId, String projectName, Long lastRunId, Instant lastRunAt, String branch,
		String gateStatus, Double passRate, Double coveragePct, long flakyCount, int trend, Visibility visibility,
		Instant brokenSince, long daysRed, long runsRed) {

	public boolean hasRuns() {
		return this.lastRunId != null;
	}

	public boolean gatePassed() {
		return "PASSED".equals(this.gateStatus);
	}

	public boolean isPrivate() {
		return this.visibility == Visibility.PRIVATE;
	}

	/** Whether the latest run is failing and we know when the red streak began. */
	public boolean isRegressed() {
		return this.brokenSince != null;
	}

	/** Compact age of the red streak: {@code today} / {@code 5d} / {@code 2w}. */
	public String daysRedLabel() {
		if (this.brokenSince == null) {
			return "";
		}
		if (this.daysRed >= 7) {
			return (this.daysRed / 7) + "w";
		}
		if (this.daysRed >= 1) {
			return this.daysRed + "d";
		}
		return "today";
	}

}
