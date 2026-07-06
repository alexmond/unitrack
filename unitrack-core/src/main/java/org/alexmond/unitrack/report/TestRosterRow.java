package org.alexmond.unitrack.report;

/**
 * One row of the Tests-page all-tests roster: a test in the latest run, with its status,
 * duration, whether it's currently flagged flaky, and whether it was just fixed (failing
 * in the previous run, passing now). Links to that test's full history.
 */
public record TestRosterRow(String className, String name, String status, long durationMs, boolean flaky,
		boolean fixed) {

	/** {@code Class › method} for display; just the name when there's no class. */
	public String displayName() {
		return (this.className == null || this.className.isBlank()) ? this.name : this.className + " › " + this.name;
	}

}
