package org.alexmond.unitrack.report;

import java.time.Instant;

/**
 * Latest run state for one branch of a project — for the Overview branches list. The
 * {@code defaultBranch} flag marks the gate base branch so it can be pinned/highlighted.
 */
public record BranchSummary(String branch, String lastStatus, double passRate, Double lineCoveragePct, Long lastRunId,
		Instant lastRunAt, long runCount, boolean defaultBranch) {
}
