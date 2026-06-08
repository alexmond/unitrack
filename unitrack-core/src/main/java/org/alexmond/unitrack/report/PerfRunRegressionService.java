package org.alexmond.unitrack.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.config.PerformanceProperties;
import org.alexmond.unitrack.domain.PerfRun;
import org.alexmond.unitrack.report.PerfRunRegression.Rule;
import org.alexmond.unitrack.repository.PerfRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates a performance/load-test run against the configured thresholds and its
 * baseline (latest prior perf run on the base branch with the same flag): p95 latency
 * increase, throughput drop, and absolute error rate.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PerfRunRegressionService {

	private final PerfRunRepository perfRuns;

	private final PerformanceProperties props;

	/** Evaluates the perf gate for a run, or empty if the run does not exist. */
	public Optional<PerfRunRegression> evaluate(Long perfRunId) {
		return this.perfRuns.findById(perfRunId).map(this::evaluate);
	}

	private PerfRunRegression evaluate(PerfRun run) {
		String baseBranch = this.props.getBaseBranch();
		PerfRun baseline = this.perfRuns
			.findFirstByProjectIdAndBranchAndFlagAndIdNotAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
					run.getProject().getId(), baseBranch, run.getFlag(), run.getId(), run.getCreatedAt())
			.orElse(null);

		List<Rule> rules = new ArrayList<>();
		boolean errorOk = run.getErrorPct() <= this.props.getMaxErrorPct();
		rules.add(new Rule("error-rate", errorOk, String.format(Locale.ROOT, "%.2f%% errors (max %.2f%%)",
				run.getErrorPct(), this.props.getMaxErrorPct())));

		if (baseline != null) {
			double base95 = baseline.getP95Ms();
			double latIncrease = (base95 > 0) ? ((run.getP95Ms() - base95) / base95 * 100.0) : 0.0;
			boolean latOk = latIncrease <= this.props.getLatencyRegressionPct();
			rules.add(new Rule("latency-p95", latOk,
					String.format(Locale.ROOT, "p95 %.0fms vs baseline %.0fms (%+.1f%%, max +%.1f%%)", run.getP95Ms(),
							base95, latIncrease, this.props.getLatencyRegressionPct())));

			double baseTput = baseline.getThroughputRps();
			double tputDrop = (baseTput > 0) ? ((baseTput - run.getThroughputRps()) / baseTput * 100.0) : 0.0;
			boolean tputOk = tputDrop <= this.props.getThroughputDropPct();
			rules.add(new Rule("throughput", tputOk,
					String.format(Locale.ROOT, "%.1f rps vs baseline %.1f (drop %.1f%%, max %.1f%%)",
							run.getThroughputRps(), baseTput, Math.max(0.0, tputDrop),
							this.props.getThroughputDropPct())));
		}

		boolean passed = rules.stream().allMatch(Rule::passed);
		Long baselineId = (baseline != null) ? baseline.getId() : null;
		return new PerfRunRegression(baseline != null, baselineId, baseBranch, passed, rules);
	}

}
