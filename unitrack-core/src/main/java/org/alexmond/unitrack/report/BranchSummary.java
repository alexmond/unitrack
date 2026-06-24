package org.alexmond.unitrack.report;

import java.time.Instant;

/**
 * Latest run state for one branch of a project — for the Overview branches list. The
 * {@code defaultBranch} flag marks the gate base branch so it can be pinned/highlighted;
 * {@code shown} is whether it surfaces by default (protected pattern, active, or default)
 * vs. collapses behind the "show all" toggle (ephemeral/merged branches).
 */
public record BranchSummary(String branch, String lastStatus, double passRate, Double lineCoveragePct, Long lastRunId,
		Instant lastRunAt, long runCount, boolean defaultBranch, boolean shown) {
}
