package org.alexmond.unitrack.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.FlakyStatus;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.TestStatus;
import org.alexmond.unitrack.report.QualityGateResult.RuleResult;
import org.alexmond.unitrack.repository.FlakyTestRepository;
import org.alexmond.unitrack.repository.TestCaseResultRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Evaluates a run against the effective per-project {@link GateConfig}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QualityGateService {

	private static final List<TestStatus> FAILED_STATUSES = List.of(TestStatus.FAILED, TestStatus.ERROR);

	private final TestRunRepository runs;

	private final TestCaseResultRepository cases;

	private final FlakyTestRepository flakyTests;

	private final ProjectSettingsService settings;

	/** Evaluates the gate for a run, or empty if the run does not exist. */
	public Optional<QualityGateResult> evaluate(Long runId) {
		return runs.findById(runId).map(this::evaluate);
	}

	/**
	 * Current minus baseline line coverage (percentage points), or empty if not
	 * computable.
	 */
	public Optional<Double> coverageDelta(Long runId) {
		return runs.findById(runId).flatMap(this::coverageDelta);
	}

	private Optional<Double> coverageDelta(TestRun run) {
		if (run.getLineCoveragePct() == null) {
			return Optional.empty();
		}
		return baselineFor(run).map(TestRun::getLineCoveragePct).map((baseCov) -> run.getLineCoveragePct() - baseCov);
	}

	private Optional<TestRun> baselineFor(TestRun run) {
		GateConfig cfg = settings.gateConfig(run.getProject().getId());
		return runs.findFirstByProjectIdAndBranchAndFlagAndIdNotAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
				run.getProject().getId(), cfg.baseBranch(), run.getFlag(), run.getId(), run.getCreatedAt());
	}

	/** Evaluates the gate for an already-loaded run (avoids a re-fetch by id). */
	public QualityGateResult evaluate(TestRun run) {
		Long projectId = run.getProject().getId();
		GateConfig cfg = settings.gateConfig(projectId);
		TestRun baseline = baselineFor(run).orElse(null);

		List<RuleResult> rules = new ArrayList<>();
		minCoverageRule(run, cfg).ifPresent(rules::add);
		coverageDropRule(run, baseline, cfg).ifPresent(rules::add);
		newFailuresRule(run, baseline, projectId, cfg).ifPresent(rules::add);

		boolean passed = rules.stream().allMatch(RuleResult::passed);
		return new QualityGateResult(passed, rules);
	}

	private Optional<RuleResult> minCoverageRule(TestRun run, GateConfig cfg) {
		Double min = cfg.minLineCoverage();
		if (min == null) {
			return Optional.empty();
		}
		Double cov = run.getLineCoveragePct();
		if (cov == null) {
			return Optional.of(new RuleResult("min-coverage", true, "no coverage reported"));
		}
		boolean ok = cov >= min;
		return Optional.of(new RuleResult("min-coverage", ok, pct(cov) + " line coverage (minimum " + pct(min) + ")"));
	}

	private Optional<RuleResult> coverageDropRule(TestRun run, TestRun baseline, GateConfig cfg) {
		if (baseline == null || run.getLineCoveragePct() == null || baseline.getLineCoveragePct() == null) {
			return Optional.empty();
		}
		double drop = baseline.getLineCoveragePct() - run.getLineCoveragePct();
		boolean ok = drop <= cfg.maxCoverageDropPct();
		String detail = pct(run.getLineCoveragePct()) + " vs baseline " + pct(baseline.getLineCoveragePct()) + " (drop "
				+ String.format(Locale.ROOT, "%.1f", Math.max(0.0, drop)) + "pp, max "
				+ String.format(Locale.ROOT, "%.1f", cfg.maxCoverageDropPct()) + "pp)";
		return Optional.of(new RuleResult("coverage-drop", ok, detail));
	}

	private Optional<RuleResult> newFailuresRule(TestRun run, TestRun baseline, Long projectId, GateConfig cfg) {
		if (!cfg.failOnNewFailures()) {
			return Optional.empty();
		}
		Set<String> current = failedKeys(run.getId());
		Set<String> baselineFails = (baseline != null) ? failedKeys(baseline.getId()) : Set.of();
		Set<String> quarantined = flakyTests.findByProjectId(projectId)
			.stream()
			.filter((ft) -> ft.getStatus() == FlakyStatus.QUARANTINED)
			.map((ft) -> key(ft.getClassName(), ft.getName()))
			.collect(Collectors.toSet());

		List<String> newFails = current.stream()
			.filter((k) -> !baselineFails.contains(k))
			.filter((k) -> !quarantined.contains(k))
			.sorted()
			.toList();

		boolean ok = newFails.isEmpty();
		String detail = ok ? "no new failures"
				: newFails.size() + " new failure(s): " + newFails.stream().limit(5).collect(Collectors.joining(", "));
		return Optional.of(new RuleResult("new-failures", ok, detail));
	}

	private Set<String> failedKeys(Long runId) {
		return cases.findByRunIdAndStatusInOrderByClassNameAscNameAsc(runId, FAILED_STATUSES)
			.stream()
			.map((c) -> key(c.getClassName(), c.getName()))
			.collect(Collectors.toSet());
	}

	private static String key(String className, String name) {
		return ((className != null) ? className : "") + ' ' + name;
	}

	private static String pct(double value) {
		return String.format(Locale.ROOT, "%.1f%%", value);
	}

}
