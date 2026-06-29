package org.alexmond.unitrack.report;

/**
 * One row of the Tests-page all-tests roster: a test in the latest run, with its status,
 * duration, and whether it's currently flagged flaky. Links to that test's full history.
 */
public record TestRosterRow(String className, String name, String status, long durationMs, boolean flaky) {

	/** {@code Class › method} for display; just the name when there's no class. */
	public String displayName() {
		return (this.className == null || this.className.isBlank()) ? this.name : this.className + " › " + this.name;
	}

}
