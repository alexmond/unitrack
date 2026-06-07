package org.alexmond.unitrack.report;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.TestStatus;
import org.alexmond.unitrack.report.TestRegressionResult.RegressedTest;
import org.alexmond.unitrack.repository.TestCaseResultRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes the {@link TestRegressionResult} diff for a run: new failures, new passes
 * (fixed), and still-failing tests relative to the latest run on the base branch. Shares
 * the quality-gate baseline (latest prior run on {@code base-branch} with the same flag).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestRegressionService {

	private static final List<TestStatus> FAILED_STATUSES = List.of(TestStatus.FAILED, TestStatus.ERROR);

	private final TestRunRepository runs;

	private final TestCaseResultRepository cases;

	private final ProjectSettingsService settings;

	/** Computes the regression diff for a run, or empty if the run does not exist. */
	public Optional<TestRegressionResult> diff(Long runId) {
		return this.runs.findById(runId).map(this::diff);
	}

	private TestRegressionResult diff(TestRun run) {
		String baseBranch = this.settings.gateConfig(run.getProject().getId()).baseBranch();
		Map<String, TestCaseResult> currentFailed = failedByKey(run.getId());
		TestRun baseline = baselineFor(run, baseBranch).orElse(null);

		if (baseline == null) {
			// No baseline to compare against: report current failures as new, nothing
			// fixed.
			List<RegressedTest> newFailures = currentFailed.values()
				.stream()
				.map(TestRegressionService::toFailure)
				.toList();
			return new TestRegressionResult(false, null, baseBranch, newFailures, List.of(), List.of());
		}

		Set<String> baselineFailed = failedKeys(baseline.getId());
		Map<String, TestStatus> currentStatus = statusByKey(run.getId());

		List<RegressedTest> newFailures = currentFailed.entrySet()
			.stream()
			.filter((e) -> !baselineFailed.contains(e.getKey()))
			.map((e) -> toFailure(e.getValue()))
			.toList();

		List<RegressedTest> stillFailing = currentFailed.entrySet()
			.stream()
			.filter((e) -> baselineFailed.contains(e.getKey()))
			.map((e) -> toFailure(e.getValue()))
			.toList();

		// Fixed: failing in the baseline and now passing in the current run.
		List<RegressedTest> newPasses = baselineFailed.stream()
			.filter((k) -> currentStatus.get(k) == TestStatus.PASSED)
			.map(TestRegressionService::toIdentity)
			.toList();

		return new TestRegressionResult(true, baseline.getId(), baseBranch, newFailures, newPasses, stillFailing);
	}

	private Optional<TestRun> baselineFor(TestRun run, String baseBranch) {
		return this.runs.findFirstByProjectIdAndBranchAndFlagAndIdNotAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
				run.getProject().getId(), baseBranch, run.getFlag(), run.getId(), run.getCreatedAt());
	}

	/** Failing cases for a run, keyed by class+name, ordered for stable output. */
	private Map<String, TestCaseResult> failedByKey(Long runId) {
		Map<String, TestCaseResult> byKey = new LinkedHashMap<>();
		for (TestCaseResult c : this.cases.findByRunIdAndStatusInOrderByClassNameAscNameAsc(runId, FAILED_STATUSES)) {
			byKey.putIfAbsent(key(c.getClassName(), c.getName()), c);
		}
		return byKey;
	}

	private Set<String> failedKeys(Long runId) {
		return this.cases.findByRunIdAndStatusInOrderByClassNameAscNameAsc(runId, FAILED_STATUSES)
			.stream()
			.map((c) -> key(c.getClassName(), c.getName()))
			.collect(Collectors.toSet());
	}

	private Map<String, TestStatus> statusByKey(Long runId) {
		Map<String, TestStatus> byKey = new TreeMap<>();
		for (TestCaseResult c : this.cases.findByRunIdOrderByStatusAscClassNameAscNameAsc(runId)) {
			byKey.putIfAbsent(key(c.getClassName(), c.getName()), c.getStatus());
		}
		return byKey;
	}

	private static RegressedTest toFailure(TestCaseResult c) {
		return new RegressedTest(c.getClassName(), c.getName(), c.getFailureType(), c.getFailureMessage());
	}

	private static RegressedTest toIdentity(String key) {
		int sep = key.indexOf(' ');
		String className = (sep > 0) ? key.substring(0, sep) : "";
		String name = (sep >= 0) ? key.substring(sep + 1) : key;
		return new RegressedTest(className, name, null, null);
	}

	private static String key(String className, String name) {
		return ((className != null) ? className : "") + ' ' + name;
	}

}
