package org.alexmond.unitrack.report;

/**
 * Per-module test totals for one run, on the Tests page. The module is derived from each
 * test's package the same way {@link ReportingService#moduleCoverage} derives coverage
 * modules, so the Tests-by-module and Coverage-by-module breakdowns line up.
 */
public record TestModuleRow(String name, int tests, int passed, int failed, int skipped) {

	/** Pass rate as a percentage (empty module counts as 100%). */
	public double passRate() {
		return (this.tests == 0) ? 100.0 : this.passed * 100.0 / this.tests;
	}

}
