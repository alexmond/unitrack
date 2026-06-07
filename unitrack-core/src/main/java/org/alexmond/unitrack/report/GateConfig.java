package org.alexmond.unitrack.report;

/**
 * Effective quality-gate configuration for a project: per-project overrides merged over
 * the global {@code unitrack.quality-gate.*} defaults.
 */
public record GateConfig(String baseBranch, Double minLineCoverage, double maxCoverageDropPct,
		boolean failOnNewFailures) {
}
