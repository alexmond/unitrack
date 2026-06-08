package org.alexmond.unitrack.report;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.config.PerformanceProperties;
import org.alexmond.unitrack.domain.PerfRun;
import org.alexmond.unitrack.domain.PerfTransaction;
import org.alexmond.unitrack.report.PerfRunDetail.LabelRow;
import org.alexmond.unitrack.repository.PerfRunRepository;
import org.alexmond.unitrack.repository.PerfTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles a {@link PerfRunDetail}: a perf run's per-label rows with baseline p95 deltas
 * + the gate verdict.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PerfRunDetailService {

	private final PerfRunRepository perfRuns;

	private final PerfTransactionRepository perfTransactions;

	private final PerfRunRegressionService perfRunRegression;

	private final PerformanceProperties props;

	public Optional<PerfRunDetail> detail(Long runId) {
		return this.perfRuns.findById(runId).map(this::build);
	}

	private PerfRunDetail build(PerfRun run) {
		PerfRun baseline = this.perfRuns
			.findFirstByProjectIdAndBranchAndFlagAndIdNotAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
					run.getProject().getId(), this.props.getBaseBranch(), run.getFlag(), run.getId(),
					run.getCreatedAt())
			.orElse(null);
		Map<String, Double> baselineP95 = (baseline != null)
				? this.perfTransactions.findByPerfRunIdOrderByMeanMsDesc(baseline.getId())
					.stream()
					.collect(Collectors.toMap(PerfTransaction::getLabel, PerfTransaction::getP95Ms, (a, b) -> a))
				: Map.of();

		List<LabelRow> labels = this.perfTransactions.findByPerfRunIdOrderByMeanMsDesc(run.getId())
			.stream()
			.map((t) -> {
				Double base = baselineP95.get(t.getLabel());
				Double delta = (base != null && base > 0) ? ((t.getP95Ms() - base) / base * 100.0) : null;
				return new LabelRow(t.getLabel(), t.getSampleCount(), t.getErrorPct(), t.getP50Ms(), t.getP90Ms(),
						t.getP95Ms(), t.getP99Ms(), base, delta);
			})
			.toList();

		PerfRunRegression regression = this.perfRunRegression.evaluate(run.getId()).orElse(null);
		return new PerfRunDetail(run.getId(), run.getProject().getId(), run.getProject().getName(), run.getCommitSha(),
				run.getBranch(), run.getFormat(), run.getCreatedAt(), run.getP50Ms(), run.getP90Ms(), run.getP95Ms(),
				run.getP99Ms(), run.getThroughputRps(), run.getErrorPct(), run.getSampleCount(), run.getDurationMs(),
				regression, labels);
	}

}
