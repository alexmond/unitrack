package org.alexmond.unitrack.report;

import java.util.List;

/** Categorization of a run's failures plus per-category counts. */
public record TriageResult(List<CategorizedCase> failures, List<CategoryCount> summary) {

	/** A failure with its assigned triage category. */
	public record CategorizedCase(String test, String status, String category) {
	}

	/** Number of failures assigned to a category. */
	public record CategoryCount(String category, int count) {
	}
}
