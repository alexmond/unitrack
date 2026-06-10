package org.alexmond.unitrack.report;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Groups a project's runs by pull/merge request, using the
 * {@code prNumber}/{@code baseBranch} captured at ingest. Read-only; the runs are the
 * source of truth (no separate PR entity).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PullRequestService {

	private final TestRunRepository runs;

	/** Open/seen pull requests for a project, most-recently-active first. */
	public List<PullRequestSummary> list(Long projectId) {
		return runs.findDistinctPrNumbers(projectId)
			.stream()
			.map((pr) -> summarize(projectId, pr))
			.filter(Objects::nonNull)
			.sorted(Comparator.comparing(PullRequestSummary::lastRunAt).reversed())
			.toList();
	}

	/** All runs for one PR, newest first (its timeline) — empty if the PR has no runs. */
	public List<TestRun> runsFor(Long projectId, Integer prNumber) {
		return runs.findByProjectIdAndPrNumberOrderByCreatedAtDesc(projectId, prNumber);
	}

	private PullRequestSummary summarize(Long projectId, Integer prNumber) {
		List<TestRun> prRuns = runs.findByProjectIdAndPrNumberOrderByCreatedAtDesc(projectId, prNumber);
		if (prRuns.isEmpty()) {
			return null;
		}
		TestRun latest = prRuns.get(0);
		return new PullRequestSummary(prNumber, latest.getBranch(), latest.getBaseBranch(), latest.getStatus(),
				latest.getCreatedAt(), latest.getId(), latest.getLineCoveragePct(), prRuns.size());
	}

}
