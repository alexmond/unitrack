package org.alexmond.unitrack.report;

/**
 * Per-module suite-time totals for one run, on the Test timing page — the timing
 * counterpart of {@link TestModuleRow}. The module is resolved the same way (explicit
 * uploader module #393, else package-derived), so all the by-module breakdowns line up.
 */
public record TestModuleTiming(String name, int tests, long totalMs) {

	/** Mean test duration in milliseconds. */
	public long avgMs() {
		return (this.tests == 0) ? 0 : this.totalMs / this.tests;
	}

}
