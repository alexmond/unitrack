package org.alexmond.unitrack.web.alert;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.alexmond.unitrack.domain.AlertEvent;
import org.alexmond.unitrack.domain.AlertKind;
import org.alexmond.unitrack.domain.FlakyStatus;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.FlakyTestService;
import org.alexmond.unitrack.report.FlakyTestView;
import org.alexmond.unitrack.report.QualityGateResult;
import org.alexmond.unitrack.report.QualityGateService;
import org.alexmond.unitrack.report.ReportingService;
import org.alexmond.unitrack.report.TestRegressionResult;
import org.alexmond.unitrack.report.TestRegressionResult.RegressedTest;
import org.alexmond.unitrack.report.TestRegressionService;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AlertEventPublisherTest {

	private final TestRegressionService regression = mock(TestRegressionService.class);

	private final QualityGateService qualityGate = mock(QualityGateService.class);

	private final FlakyTestService flaky = mock(FlakyTestService.class);

	private final ReportingService reporting = mock(ReportingService.class);

	private final TestRunRepository runs = mock(TestRunRepository.class);

	private final CapturingSink sink = new CapturingSink();

	private final AlertEventPublisher publisher = new AlertEventPublisher(this.regression, this.qualityGate, this.flaky,
			this.reporting, this.runs, List.of(this.sink));

	private TestRun run(long id) {
		TestRun run = mock(TestRun.class);
		given(run.getId()).willReturn(id);
		given(run.getProject()).willReturn(new Project("proj", null));
		given(run.getBranch()).willReturn("main");
		given(run.getFlag()).willReturn("default");
		given(run.getCreatedAt()).willReturn(Instant.parse("2026-06-17T00:00:00Z"));
		// Defaults: previous run was green (newly red) and no flaky failures.
		given(this.runs.findFirstByProjectIdAndBranchAndFlagAndCreatedAtLessThanOrderByCreatedAtDesc(any(), any(),
				any(), any()))
			.willReturn(Optional.empty());
		given(this.reporting.failedCasesFor(id)).willReturn(List.of());
		return run;
	}

	private static QualityGateResult failed() {
		return new QualityGateResult(false, List.of());
	}

	@Test
	void emitsGateRegressionAndCoverageEvents() {
		TestRun run = run(7L);
		given(this.regression.diff(7L)).willReturn(Optional.of(new TestRegressionResult(true, 1L, "main",
				List.of(new RegressedTest("C", "m", null, null)), List.of(), List.of())));
		given(this.qualityGate.coverageDelta(7L)).willReturn(Optional.of(-2.5));

		this.publisher.publishForRun(run, failed());

		assertThat(this.sink.events).extracting(AlertEvent::kind)
			.containsExactlyInAnyOrder(AlertKind.GATE_FAILED, AlertKind.NEW_REGRESSION, AlertKind.COVERAGE_DROPPED);
	}

	@Test
	void emitsNothingForACleanRun() {
		TestRun run = run(8L);
		given(this.regression.diff(8L)).willReturn(Optional.empty());
		given(this.qualityGate.coverageDelta(8L)).willReturn(Optional.of(1.0));

		this.publisher.publishForRun(run, new QualityGateResult(true, List.of()));

		assertThat(this.sink.events).isEmpty();
	}

	@Test
	void gateFailureMutedWhenPreviousRunWasAlsoRed() {
		TestRun run = run(9L);
		given(this.regression.diff(9L)).willReturn(Optional.empty());
		given(this.qualityGate.coverageDelta(9L)).willReturn(Optional.empty());
		TestRun previous = mock(TestRun.class);
		given(previous.getStatus()).willReturn("FAILED");
		given(this.runs.findFirstByProjectIdAndBranchAndFlagAndCreatedAtLessThanOrderByCreatedAtDesc(any(), any(),
				any(), any()))
			.willReturn(Optional.of(previous));

		this.publisher.publishForRun(run, failed());

		// Still red, not a transition — no ping.
		assertThat(this.sink.events).isEmpty();
	}

	@Test
	void gateFailureMutedWhenEveryFailureIsFlaky() {
		TestRun run = run(10L);
		given(this.regression.diff(10L)).willReturn(Optional.empty());
		given(this.qualityGate.coverageDelta(10L)).willReturn(Optional.empty());
		TestCaseResult flakyCase = mock(TestCaseResult.class);
		given(flakyCase.getClassName()).willReturn("pkg.T");
		given(flakyCase.getName()).willReturn("a");
		given(this.reporting.failedCasesFor(10L)).willReturn(List.of(flakyCase));
		given(this.flaky.listFlaky(any()))
			.willReturn(List.of(new FlakyTestView("pkg.T", "a", 1, 10, 5, 50.0, null, FlakyStatus.ACTIVE, null)));

		this.publisher.publishForRun(run, failed());

		// All-flaky noise — suppressed.
		assertThat(this.sink.events).isEmpty();
	}

	@Test
	void aThrowingSinkIsIsolatedAndNeverPropagates() {
		TestRun run = run(11L);
		given(this.regression.diff(11L)).willReturn(Optional.empty());
		given(this.qualityGate.coverageDelta(11L)).willReturn(Optional.empty());
		AlertSink boom = (e) -> {
			throw new IllegalStateException("channel down");
		};
		AlertEventPublisher twoSinks = new AlertEventPublisher(this.regression, this.qualityGate, this.flaky,
				this.reporting, this.runs, List.of(boom, this.sink));

		assertThatCode(() -> twoSinks.publishForRun(run, failed())).doesNotThrowAnyException();
		assertThat(this.sink.events).extracting(AlertEvent::kind).containsExactly(AlertKind.GATE_FAILED);
	}

	private static final class CapturingSink implements AlertSink {

		private final List<AlertEvent> events = new ArrayList<>();

		@Override
		public void publish(AlertEvent event) {
			this.events.add(event);
		}

	}

}
