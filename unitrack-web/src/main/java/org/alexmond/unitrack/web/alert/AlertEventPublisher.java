package org.alexmond.unitrack.web.alert;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.AlertEvent;
import org.alexmond.unitrack.domain.AlertKind;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.FlakyTestService;
import org.alexmond.unitrack.report.QualityGateResult;
import org.alexmond.unitrack.report.QualityGateService;
import org.alexmond.unitrack.report.ReportingService;
import org.alexmond.unitrack.report.TestRegressionResult;
import org.alexmond.unitrack.report.TestRegressionService;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns post-ingest results into {@link AlertEvent}s and fans them out to every
 * {@link AlertSink}. Noise-muting keeps pings meaningful (#242): a gate-failure alert
 * fires only on a green→red <em>transition</em> (not on every repeated red run) and is
 * suppressed when every failure is a known-flaky test. <strong>Best-effort and
 * isolated</strong>: a failure here (or in any sink) is logged and swallowed, never
 * propagated.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(AlertEventPublisher.class);

	private static final String PASSED = "PASSED";

	private final TestRegressionService regression;

	private final QualityGateService qualityGate;

	private final FlakyTestService flaky;

	private final ReportingService reporting;

	private final TestRunRepository runs;

	private final List<AlertSink> sinks;

	/**
	 * Emits the events a run warrants (gate failed / new regression / coverage dropped).
	 */
	public void publishForRun(TestRun run, QualityGateResult gate) {
		try {
			// Gate-failure: only on a green→red transition, and not when it's all flaky
			// noise.
			if (gate != null && !gate.passed() && isNewlyRed(run) && !allFailuresFlaky(run)) {
				emit(run, AlertKind.GATE_FAILED, "Quality gate failed: " + gate.status());
			}
			// New regressions are inherently a transition (new failing tests vs the
			// baseline).
			this.regression.diff(run.getId())
				.filter(TestRegressionResult::hasRegressions)
				.ifPresent((r) -> emit(run, AlertKind.NEW_REGRESSION,
						r.newFailureCount() + " new failing test(s) vs baseline"));
			this.qualityGate.coverageDelta(run.getId())
				.filter((d) -> d < 0)
				.ifPresent((d) -> emit(run, AlertKind.COVERAGE_DROPPED,
						String.format("Coverage dropped %.1f pp vs baseline", d)));
		}
		catch (RuntimeException ex) {
			log.warn("Alert publishing failed for run {} (ignored)", run.getId(), ex);
		}
	}

	/**
	 * True when the previous run on the same branch+flag was passing (or there is none).
	 */
	private boolean isNewlyRed(TestRun run) {
		return this.runs
			.findFirstByProjectIdAndBranchAndFlagAndCreatedAtLessThanOrderByCreatedAtDesc(run.getProject().getId(),
					run.getBranch(), run.getFlag(), run.getCreatedAt())
			.map((prev) -> PASSED.equals(prev.getStatus()))
			.orElse(true);
	}

	/**
	 * True when every failing test in the run is already tracked as flaky (likely noise).
	 */
	private boolean allFailuresFlaky(TestRun run) {
		List<TestCaseResult> failures = this.reporting.failedCasesFor(run.getId());
		if (failures.isEmpty()) {
			return false;
		}
		Set<String> flakyKeys = this.flaky.listFlaky(run.getProject().getId())
			.stream()
			.map((f) -> key(f.className(), f.name()))
			.collect(Collectors.toSet());
		return failures.stream().allMatch((c) -> flakyKeys.contains(key(c.getClassName(), c.getName())));
	}

	private static String key(String className, String name) {
		return ((className == null || className.isBlank()) ? "" : className + "#") + name;
	}

	private void emit(TestRun run, AlertKind kind, String message) {
		AlertEvent event = new AlertEvent(run.getProject().getId(), run.getProject().getName(), kind, run.getId(),
				message);
		for (AlertSink sink : this.sinks) {
			try {
				sink.publish(event);
			}
			catch (RuntimeException ex) {
				log.warn("Alert sink {} failed for {} (ignored)", sink.getClass().getSimpleName(), kind, ex);
			}
		}
	}

}
