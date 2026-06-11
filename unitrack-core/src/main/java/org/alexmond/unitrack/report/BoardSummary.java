package org.alexmond.unitrack.report;

/**
 * Aggregate health across all projects, for the KPI strip atop the home board. Derived
 * from the already-built {@link ProjectHealth} list, so it costs no extra queries.
 * {@code avgCoveragePct} is null when no project reports coverage.
 */
public record BoardSummary(int projectCount, long failingGates, long flakyTotal, Double avgCoveragePct) {
}
