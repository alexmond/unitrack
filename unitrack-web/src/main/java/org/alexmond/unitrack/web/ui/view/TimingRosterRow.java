package org.alexmond.unitrack.web.ui.view;

/**
 * One row of the Test-timing "slowest tests" roster: a test's duration in the latest run
 * plus its change vs the same test in the previous run ({@code deltaMs}, null when the
 * test is new / absent last run).
 *
 * @param suiteName the owning suite
 * @param className the test class
 * @param name the test method
 * @param status the latest-run status
 * @param durationMs the latest-run duration
 * @param deltaMs change vs the previous run (ms), or null when new/absent last run
 */
public record TimingRosterRow(String suiteName, String className, String name, String status, long durationMs,
		Long deltaMs) {

	/** "Class › method" for display + search. */
	public String displayName() {
		return className + " › " + name;
	}

}
