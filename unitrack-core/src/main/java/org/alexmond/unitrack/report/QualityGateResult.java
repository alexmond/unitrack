package org.alexmond.unitrack.report;

import java.util.List;

/** Outcome of evaluating a run against the quality gate. */
public record QualityGateResult(boolean passed, List<RuleResult> rules) {

	public String status() {
		return passed ? "PASSED" : "FAILED";
	}

	/** Result of a single gate rule. */
	public record RuleResult(String name, boolean passed, String detail) {
	}
}
