package org.alexmond.unitrack.report;

import java.util.List;

import org.alexmond.unitrack.domain.TestRun;

/**
 * A diff between two runs: which tests newly fail, which got fixed, which still fail,
 * plus coverage / pass-rate / duration deltas (head minus base). Test entries are
 * {@code class#name} strings.
 */
public record RunComparison(TestRun base, TestRun head, List<String> newlyFailing, List<String> fixed,
		List<String> stillFailing, Double coverageDelta, long durationDeltaMs, double passRateDelta) {

}
