package org.alexmond.unitrack.report;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.TestStatus;
import org.alexmond.unitrack.repository.TestCaseResultRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes, for a currently-failing test, the run/commit where its failing streak began
 * on the same branch -- the first run that failed since the test was last green. Uses
 * only the stored run history (commit SHAs), not git diffs.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlameService {

	private static final int HISTORY_LIMIT = 100;

	private static final List<TestStatus> FAILED_STATUSES = List.of(TestStatus.FAILED, TestStatus.ERROR);

	private final TestRunRepository runs;

	private final TestCaseResultRepository cases;

	/**
	 * Blame entries for a run's current failures, keyed by case id — for the run page.
	 */
	public Map<Long, BlameEntry> blameByCaseId(TestRun run, List<TestCaseResult> failures) {
		Map<Long, BlameEntry> byCase = new LinkedHashMap<>();
		for (TestCaseResult c : failures) {
			firstFailing(run, c.getClassName(), c.getName()).ifPresent((entry) -> byCase.put(c.getId(), entry));
		}
		return byCase;
	}

	/** Blame entries for a run's current failures — for the REST API. */
	public List<BlameEntry> blame(Long runId) {
		return this.runs.findById(runId).map((run) -> {
			List<TestCaseResult> failures = this.cases.findByRunIdAndStatusInOrderByClassNameAscNameAsc(runId,
					FAILED_STATUSES);
			return failures.stream()
				.map((c) -> firstFailing(run, c.getClassName(), c.getName()).orElse(null))
				.filter(Objects::nonNull)
				.toList();
		}).orElseGet(List::of);
	}

	/**
	 * Blame for a test that may be currently failing across a project, without a specific
	 * run as anchor. Uses the latest result's branch (if non-null) the same way the
	 * run-scoped method does, then falls back to all branches.
	 * <p>
	 * Returns empty when the latest result for this test is not in a failing state.
	 */
	public Optional<BlameEntry> firstFailingForTest(Long projectId, String className, String name) {
		List<TestCaseResult> history = this.cases.findTestHistory(projectId, className, name,
				PageRequest.ofSize(HISTORY_LIMIT));
		if (history.isEmpty()) {
			return Optional.empty();
		}
		TestCaseResult latest = history.get(0);
		if (!FAILED_STATUSES.contains(latest.getStatus())) {
			return Optional.empty();
		}
		// Re-fetch on the branch when possible (mirrors run-scoped behaviour).
		String branch = latest.getRun().getBranch();
		List<TestCaseResult> branchHistory = (branch != null) ? this.cases.findTestHistoryOnBranch(projectId, branch,
				className, name, PageRequest.ofSize(HISTORY_LIMIT)) : history;
		return walkStreakFrom(branchHistory, latest.getRun().getCreatedAt(), className, name);
	}

	private Optional<BlameEntry> firstFailing(TestRun run, String className, String name) {
		Long projectId = run.getProject().getId();
		List<TestCaseResult> history = (run.getBranch() != null)
				? this.cases.findTestHistoryOnBranch(projectId, run.getBranch(), className, name,
						PageRequest.ofSize(HISTORY_LIMIT))
				: this.cases.findTestHistory(projectId, className, name, PageRequest.ofSize(HISTORY_LIMIT));
		return walkStreakFrom(history, run.getCreatedAt(), className, name);
	}

	/**
	 * Shared streak-walk: given a newest-first history list, walk from the anchor instant
	 * backward through a contiguous failing streak and return a {@link BlameEntry} for
	 * the oldest still-failing run.
	 */
	private static Optional<BlameEntry> walkStreakFrom(List<TestCaseResult> history, Instant anchorCreatedAt,
			String className, String name) {
		TestCaseResult firstFailing = null;
		for (TestCaseResult c : history) {
			if (c.getRun().getCreatedAt().isAfter(anchorCreatedAt)) {
				continue;
			}
			if (FAILED_STATUSES.contains(c.getStatus())) {
				firstFailing = c;
			}
			else {
				break;
			}
		}
		return Optional.ofNullable(firstFailing).map((c) -> {
			TestRun r = c.getRun();
			return new BlameEntry(className, name, r.getId(), r.getCommitSha(), r.getShortSha());
		});
	}

}
