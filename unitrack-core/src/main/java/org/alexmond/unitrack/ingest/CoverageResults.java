package org.alexmond.unitrack.ingest;

import java.util.List;

/** Parsed JaCoCo report: top-level counters plus per-file breakdown. */
public record CoverageResults(
        int lineCovered, int lineMissed,
        int branchCovered, int branchMissed,
        int instructionCovered, int instructionMissed,
        int methodCovered, int methodMissed,
        List<ParsedFileCoverage> files) {

    /** A single parsed source file's coverage counters. */
    public record ParsedFileCoverage(
            String packageName,
            String fileName,
            int lineCovered, int lineMissed,
            int branchCovered, int branchMissed) {
    }
}
