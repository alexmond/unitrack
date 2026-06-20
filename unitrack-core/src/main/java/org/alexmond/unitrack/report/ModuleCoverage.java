package org.alexmond.unitrack.report;

import org.alexmond.unitrack.domain.CoverageReport;

/**
 * Aggregated coverage for one module of a multi-module project, derived from the package
 * structure of a coverage report (the segment that follows the longest package prefix
 * common to every file). Lets a flat multi-module upload still be read per module without
 * any module concept at ingest time.
 */
public record ModuleCoverage(String name, int lineCovered, int lineMissed, int branchCovered, int branchMissed,
		int files) {

	public double linePct() {
		return CoverageReport.pct(lineCovered, lineMissed);
	}

	public double branchPct() {
		return CoverageReport.pct(branchCovered, branchMissed);
	}

	public int lineTotal() {
		return lineCovered + lineMissed;
	}

}
