package org.alexmond.unitrack.report;

/**
 * Per-module test totals for one run, on the Tests page. The module is the explicit
 * module the uploader tagged on each result (#393/#423); untagged results fall under
 * {@code (none)}, and a run with no tagged modules shows no by-module breakdown.
 * (Coverage — which has no per-test module — still derives its modules from the package
 * tree in {@link ReportingService#moduleCoverage}.)
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
