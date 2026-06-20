package org.alexmond.unitrack.report;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.config.PerformanceProperties;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.PerfRegressionResult.Slowdown;
import org.alexmond.unitrack.repository.TestCaseResultRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detects slow-test duration regressions: tests that ran significantly slower in a run
 * than in its baseline (latest prior run on the configured base branch with the same
 * flag). Pure read-side over the per-test {@code durationMs} ingestion already stores.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PerfRegressionService {

	private final TestRunRepository runs;

	private final TestCaseResultRepository cases;

	private final PerformanceProperties props;

	/** Slow-test regression diff for a run, or empty if the run does not exist. */
	@Cacheable(value = "perfRegression", key = "#runId")
	public Optional<PerfRegressionResult> diff(Long runId) {
		return this.runs.findById(runId).map(this::diff);
	}

	private PerfRegressionResult diff(TestRun run) {
		TestRun baseline = baselineFor(run).orElse(null);
		if (baseline == null) {
			return new PerfRegressionResult(false, null, this.props.getBaseBranch(), List.of());
		}
		Map<String, Long> baselineDurations = durationsByKey(baseline.getId());
		List<Slowdown> slower = durationsByKey(run.getId()).entrySet().stream().map((e) -> {
			Long base = baselineDurations.get(e.getKey());
			if (base == null) {
				return null;
			}
			long delta = e.getValue() - base;
			double pct = (base > 0) ? (delta * 100.0 / base) : 0.0;
			boolean regressed = delta >= this.props.getSlowdownMinMs() && pct >= this.props.getSlowdownPct();
			return regressed ? toSlowdown(e.getKey(), base, e.getValue(), delta, pct) : null;
		}).filter((s) -> s != null).sorted(Comparator.comparingLong(Slowdown::deltaMs).reversed()).toList();
		return new PerfRegressionResult(true, baseline.getId(), this.props.getBaseBranch(), slower);
	}

	private Optional<TestRun> baselineFor(TestRun run) {
		return this.runs.findFirstByProjectIdAndBranchAndFlagAndIdNotAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
				run.getProject().getId(), this.props.getBaseBranch(), run.getFlag(), run.getId(), run.getCreatedAt());
	}

	/** All of a run's cases as key -> durationMs (first wins on duplicate keys). */
	private Map<String, Long> durationsByKey(Long runId) {
		Map<String, Long> byKey = new LinkedHashMap<>();
		for (TestCaseResult c : this.cases.findByRunIdOrderByStatusAscClassNameAscNameAsc(runId)) {
			byKey.putIfAbsent(key(c.getClassName(), c.getName()), c.getDurationMs());
		}
		return byKey;
	}

	private static Slowdown toSlowdown(String key, long baseMs, long currentMs, long deltaMs, double pct) {
		int sep = key.indexOf(' ');
		String className = (sep > 0) ? key.substring(0, sep) : "";
		String name = (sep >= 0) ? key.substring(sep + 1) : key;
		return new Slowdown(className, name, baseMs, currentMs, deltaMs, Math.round(pct * 10.0) / 10.0);
	}

	private static String key(String className, String name) {
		return ((className != null) ? className : "") + ' ' + name;
	}

}
