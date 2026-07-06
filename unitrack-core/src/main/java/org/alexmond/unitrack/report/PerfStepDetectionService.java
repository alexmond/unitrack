package org.alexmond.unitrack.report;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.PerfRun;
import org.alexmond.unitrack.repository.PerfRunRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the {@link ChangePointDetector} over one perf series' p95-latency history to
 * surface a sustained regression and when it started (#379). Latency is higher-is-worse;
 * other metrics (throughput, error rate) are a follow-up.
 */
@Service
@RequiredArgsConstructor
public class PerfStepDetectionService {

	private static final int SERIES_LIMIT = 30;

	private final PerfRunRepository perfRuns;

	private final ChangePointDetector detector = new ChangePointDetector();

	/**
	 * A sustained p95 step for {@code (project, flag)}, or empty if stable / too few
	 * runs.
	 */
	@Transactional(readOnly = true)
	public Optional<PerfStepSignal> detectLatencyStep(Long projectId, String flag) {
		if (flag == null || flag.isBlank()) {
			return Optional.empty();
		}
		List<PerfRun> ordered = this.perfRuns
			.findByProjectIdAndFlagOrderByCreatedAtDescIdDesc(projectId, flag, PageRequest.ofSize(SERIES_LIMIT))
			.reversed();
		double[] values = ordered.stream().mapToDouble(PerfRun::getP95Ms).toArray();
		ChangePointDetector.Result result = this.detector.detect(values, true, ChangePointDetector.Config.defaults());
		if (!result.regressed()) {
			return Optional.empty();
		}
		PerfRun onset = ordered.get(result.onsetIndex());
		return Optional.of(new PerfStepSignal(onset.getCommitSha(), onset.getCreatedAt(), result.depthZ(),
				result.baselineMedian(), result.recentMedian(), onset.getId()));
	}

}
