package org.alexmond.unitrack.web.live;

import org.alexmond.unitrack.domain.TestRun;

/**
 * The compact payload pushed to live subscribers when a run is ingested — just enough for
 * the dashboard to update a project card or prepend a runs-table row without a reload.
 */
public record RunUpdate(Long projectId, Long runId, String branch, String flag, String status, int totalTests,
		int failed, int skipped, double passRatePct, Double lineCoveragePct, long durationMs, String shortSha,
		String createdAt) {

	public static RunUpdate of(TestRun run) {
		return new RunUpdate(run.getProject().getId(), run.getId(), run.getBranch(), run.getFlag(), run.getStatus(),
				run.getTotalTests(), run.getFailed() + run.getErrors(), run.getSkipped(), run.passRate(),
				run.getLineCoveragePct(), run.getDurationMs(), run.getShortSha(), String.valueOf(run.getCreatedAt()));
	}

}
