package org.alexmond.unitrack.report;

import org.alexmond.unitrack.domain.CoverageReport;

/** Aggregated line/branch coverage for one package within a {@link CoverageReport}. */
public record CoveragePackage(String packageName, int lineCovered, int lineMissed, int branchCovered,
		int branchMissed) {

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
