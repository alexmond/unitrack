package org.alexmond.unitrack.report;

import java.util.List;

/**
 * Per-file line-coverage change of a run versus its baseline (the latest prior run on the
 * base branch with the same flag).
 */
public record CoverageDiff(Long baselineRunId, String baseBranch, double totalDelta, List<FileDelta> files) {

	public boolean isEmpty() {
		return files.isEmpty();
	}

	/** How a single file's coverage changed relative to the baseline. */
	public enum Kind {

		IMPROVED, DROPPED, ADDED, REMOVED

	}

	/**
	 * One file's change. {@code basePct} is null for {@link Kind#ADDED};
	 * {@code currentPct} is null for {@link Kind#REMOVED}. {@code delta} is current minus
	 * base (percentage points), signed so the most negative sorts first.
	 */
	public record FileDelta(String path, Double basePct, Double currentPct, double delta, Kind kind) {
	}

}
