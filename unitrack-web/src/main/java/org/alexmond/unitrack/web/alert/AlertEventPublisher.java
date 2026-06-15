package org.alexmond.unitrack.web.alert;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.AlertEvent;
import org.alexmond.unitrack.domain.AlertKind;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.QualityGateResult;
import org.alexmond.unitrack.report.QualityGateService;
import org.alexmond.unitrack.report.TestRegressionResult;
import org.alexmond.unitrack.report.TestRegressionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns post-ingest results into {@link AlertEvent}s and fans them out to every
 * {@link AlertSink}. <strong>Best-effort and isolated</strong>: a failure here (or in any
 * sink) is logged and swallowed, never propagated — emitting an alert must not fail or
 * slow an ingest.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(AlertEventPublisher.class);

	private final TestRegressionService regression;

	private final QualityGateService qualityGate;

	private final List<AlertSink> sinks;

	/**
	 * Emits the events a run warrants (gate failed / new regression / coverage dropped).
	 */
	public void publishForRun(TestRun run, QualityGateResult gate) {
		try {
			if (gate != null && !gate.passed()) {
				emit(run, AlertKind.GATE_FAILED, "Quality gate failed: " + gate.status());
			}
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
