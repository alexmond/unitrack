package org.alexmond.unitrack.report;

import java.util.List;
import java.util.Optional;

import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.TestStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ComparisonServiceTest {

	private final ReportingService reporting = mock(ReportingService.class);

	private final ComparisonService service = new ComparisonService(reporting);

	private TestCaseResult caseWith(String className, String name, TestStatus status) {
		TestCaseResult c = mock(TestCaseResult.class);
		given(c.getClassName()).willReturn(className);
		given(c.getName()).willReturn(name);
		given(c.getStatus()).willReturn(status);
		return c;
	}

	private TestRun run(long id, long durationMs, Double coverage, double passRate) {
		TestRun r = mock(TestRun.class);
		given(r.getDurationMs()).willReturn(durationMs);
		given(r.getLineCoveragePct()).willReturn(coverage);
		given(r.passRate()).willReturn(passRate);
		given(reporting.findRun(id)).willReturn(Optional.of(r));
		return r;
	}

	@Test
	void emptyWhenEitherRunMissing() {
		TestRun present = mock(TestRun.class);
		given(reporting.findRun(1L)).willReturn(Optional.empty());
		given(reporting.findRun(2L)).willReturn(Optional.of(present));

		assertThat(service.compare(1L, 2L)).isEmpty();
	}

	@Test
	void classifiesNewlyFailingFixedAndStillFailing() {
		run(1L, 1000L, 80.0, 75.0);
		run(2L, 1500L, 85.0, 50.0);
		// base: A passes, B fails, C fails. head: A fails, B passes, C fails.
		TestCaseResult baseA = caseWith("pkg.T", "a", TestStatus.PASSED);
		TestCaseResult baseB = caseWith("pkg.T", "b", TestStatus.FAILED);
		TestCaseResult baseC = caseWith("pkg.T", "c", TestStatus.ERROR);
		TestCaseResult headA = caseWith("pkg.T", "a", TestStatus.FAILED);
		TestCaseResult headB = caseWith("pkg.T", "b", TestStatus.PASSED);
		TestCaseResult headC = caseWith("pkg.T", "c", TestStatus.FAILED);
		given(reporting.allCasesFor(1L)).willReturn(List.of(baseA, baseB, baseC));
		given(reporting.allCasesFor(2L)).willReturn(List.of(headA, headB, headC));

		RunComparison cmp = service.compare(1L, 2L).orElseThrow();

		assertThat(cmp.newlyFailing()).containsExactly("pkg.T#a");
		assertThat(cmp.fixed()).containsExactly("pkg.T#b");
		assertThat(cmp.stillFailing()).containsExactly("pkg.T#c");
		assertThat(cmp.coverageDelta()).isEqualTo(5.0);
		assertThat(cmp.durationDeltaMs()).isEqualTo(500L);
		assertThat(cmp.passRateDelta()).isEqualTo(-25.0);
	}

	@Test
	void coverageDeltaNullWhenEitherSideLacksCoverage() {
		run(1L, 1000L, null, 100.0);
		run(2L, 1000L, 90.0, 100.0);
		given(reporting.allCasesFor(1L)).willReturn(List.of());
		given(reporting.allCasesFor(2L)).willReturn(List.of());

		assertThat(service.compare(1L, 2L).orElseThrow().coverageDelta()).isNull();
	}

}
