package org.alexmond.unitrack.report;

import java.time.Instant;

/**
 * One pull/merge request grouped from its runs: the head branch the change is on, the
 * base branch it targets, and the state of its most recent run.
 */
public record PullRequestSummary(int number, String headBranch, String baseBranch, String lastStatus, Instant lastRunAt,
		long lastRunId, Double lastCoveragePct, int runCount) {
}
