package org.alexmond.unitrack.web.alert;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.alexmond.unitrack.domain.AlertEvent;
import org.alexmond.unitrack.domain.AlertKind;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.QualityGateResult;
import org.alexmond.unitrack.report.QualityGateService;
import org.alexmond.unitrack.report.TestRegressionResult;
import org.alexmond.unitrack.report.TestRegressionResult.RegressedTest;
import org.alexmond.unitrack.report.TestRegressionService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AlertEventPublisherTest {

	private final TestRegressionService regression = mock(TestRegressionService.class);

	private final QualityGateService qualityGate = mock(QualityGateService.class);

	private final CapturingSink sink = new CapturingSink();

	private TestRun run(long id) {
		TestRun run = mock(TestRun.class);
		given(run.getId()).willReturn(id);
		given(run.getProject()).willReturn(new Project("proj", null));
		return run;
	}

	@Test
	void emitsGateRegressionAndCoverageEvents() {
		TestRun run = run(7L);
		given(this.regression.diff(7L)).willReturn(Optional.of(new TestRegressionResult(true, 1L, "main",
				List.of(new RegressedTest("C", "m", null, null)), List.of(), List.of())));
		given(this.qualityGate.coverageDelta(7L)).willReturn(Optional.of(-2.5));

		publisher(this.sink).publishForRun(run, new QualityGateResult(false, List.of()));

		assertThat(this.sink.events).extracting(AlertEvent::kind)
			.containsExactlyInAnyOrder(AlertKind.GATE_FAILED, AlertKind.NEW_REGRESSION, AlertKind.COVERAGE_DROPPED);
	}

	@Test
	void emitsNothingForAcleanRun() {
		TestRun run = run(8L);
		given(this.regression.diff(8L)).willReturn(Optional.empty());
		given(this.qualityGate.coverageDelta(8L)).willReturn(Optional.of(1.0));

		publisher(this.sink).publishForRun(run, new QualityGateResult(true, List.of()));

		assertThat(this.sink.events).isEmpty();
	}

	@Test
	void aThrowingSinkIsIsolatedAndNeverPropagates() {
		TestRun run = run(9L);
		given(this.regression.diff(9L)).willReturn(Optional.empty());
		given(this.qualityGate.coverageDelta(9L)).willReturn(Optional.empty());
		AlertSink boom = (e) -> {
			throw new IllegalStateException("channel down");
		};

		assertThatCode(() -> publisher(boom, this.sink).publishForRun(run, new QualityGateResult(false, List.of())))
			.doesNotThrowAnyException();
		// The healthy sink still received the gate-failed event despite the broken one.
		assertThat(this.sink.events).extracting(AlertEvent::kind).containsExactly(AlertKind.GATE_FAILED);
	}

	private AlertEventPublisher publisher(AlertSink... sinks) {
		return new AlertEventPublisher(this.regression, this.qualityGate, List.of(sinks));
	}

	private static final class CapturingSink implements AlertSink {

		private final List<AlertEvent> events = new ArrayList<>();

		@Override
		public void publish(AlertEvent event) {
			this.events.add(event);
		}

	}

}
