package org.alexmond.unitrack.report;

/**
 * Per-module test totals for one run, on the Tests page. The module is derived from each
 * test's package the same way {@link ReportingService#moduleCoverage} derives coverage
 * modules, so the Tests-by-module and Coverage-by-module breakdowns line up.
 */
public record TestModuleRow(String name, int tests, int passed, int failed, int skipped) {

	/**
	 * Pass rate over <em>executed</em> tests (skipped excluded), matching
	 * {@code TestRun#passRate()} and the scoped-KPI {@code passRate} so a module reads
	 * the same in the breakdown row and after you click into it. Nothing executed → 0.0.
	 */
	public double passRate() {
		int considered = this.tests - this.skipped;
		return (considered <= 0) ? 0.0 : this.passed * 100.0 / considered;
	}

}
