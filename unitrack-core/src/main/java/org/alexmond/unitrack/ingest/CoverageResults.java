package org.alexmond.unitrack.ingest;

import java.util.List;

/** Parsed JaCoCo report: top-level counters plus per-file breakdown. */
public record CoverageResults(int lineCovered, int lineMissed, int branchCovered, int branchMissed,
		int instructionCovered, int instructionMissed, int methodCovered, int methodMissed,
		List<ParsedFileCoverage> files) {

	/**
	 * A single parsed source file's coverage counters, plus the line numbers with no
	 * coverage ({@code uncoveredLines}) — used to annotate a PR's changed lines (#443).
	 * Formats that don't carry per-line data use the 6-arg constructor (empty list).
	 */
	public record ParsedFileCoverage(String packageName, String fileName, int lineCovered, int lineMissed,
			int branchCovered, int branchMissed, List<Integer> uncoveredLines) {

		public ParsedFileCoverage(String packageName, String fileName, int lineCovered, int lineMissed,
				int branchCovered, int branchMissed) {
			this(packageName, fileName, lineCovered, lineMissed, branchCovered, branchMissed, List.of());
		}
	}
}
