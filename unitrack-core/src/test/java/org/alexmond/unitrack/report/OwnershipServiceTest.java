package org.alexmond.unitrack.report;

import java.util.List;
import java.util.Map;

import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestOwnerRule;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.alexmond.unitrack.repository.TestOwnerRuleRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class OwnershipServiceTest {

	private final TestOwnerRuleRepository rules = mock(TestOwnerRuleRepository.class);

	private final ReportingService reporting = mock(ReportingService.class);

	private final FlakyTestService flaky = mock(FlakyTestService.class);

	private final OwnershipService service = new OwnershipService(rules, mock(ProjectRepository.class), reporting,
			flaky);

	private TestCaseResult caseWith(long id, String className) {
		TestCaseResult c = mock(TestCaseResult.class);
		given(c.getId()).willReturn(id);
		given(c.getClassName()).willReturn(className);
		return c;
	}

	@Test
	void attributesOwnersByClassNameRegexFirstMatchWins() {
		given(rules.findByProjectIdOrderByPriorityAscIdAsc(1L))
			.willReturn(List.of(new TestOwnerRule(null, "@billing", "com\\.billing\\..*", 100),
					new TestOwnerRule(null, "@web", "com\\.web\\..*", 200)));

		Map<Long, String> owners = service.ownerByCaseId(1L, List.of(caseWith(1, "com.billing.InvoiceTest"),
				caseWith(2, "com.web.HomeTest"), caseWith(3, "com.other.Thing")));

		assertThat(owners).containsEntry(1L, "@billing").containsEntry(2L, "@web").containsEntry(3L, null);
	}

	@Test
	void lowerPriorityRuleShadowsAndInvalidRegexFallsBackToSubstring() {
		given(rules.findByProjectIdOrderByPriorityAscIdAsc(1L))
			.willReturn(List.of(new TestOwnerRule(null, "@catch-all", "billing", 50),
					new TestOwnerRule(null, "@billing", "com\\.billing\\..*", 100)));

		Map<Long, String> owners = service.ownerByCaseId(1L, List.of(caseWith(1, "com.billing.InvoiceTest")));

		// Priority 50 wins; "billing" is a valid regex that .find()s, so substring isn't
		// even needed.
		assertThat(owners).containsEntry(1L, "@catch-all");
	}

	@Test
	void scorecardCountsFailingAndFlakyByOwnerWithUnassignedBucket() {
		given(rules.findByProjectIdOrderByPriorityAscIdAsc(1L))
			.willReturn(List.of(new TestOwnerRule(null, "@billing", "com\\.billing\\..*", 100)));
		TestCaseResult c1 = caseWith(1, "com.billing.InvoiceTest");
		TestCaseResult c2 = caseWith(2, "com.web.HomeTest");
		TestRun latest = mock(TestRun.class);
		given(latest.getId()).willReturn(10L);
		given(reporting.recentRuns(1L, 1)).willReturn(List.of(latest));
		given(reporting.failedCasesFor(10L)).willReturn(List.of(c1, c2));
		FlakyTestView flake = new FlakyTestView("com.billing.FlakyTest", "sometimes", 1, 10, 5, 50.0, null,
				org.alexmond.unitrack.domain.FlakyStatus.ACTIVE, null);
		given(flaky.listFlaky(1L)).willReturn(List.of(flake));

		List<OwnershipService.OwnerScore> scores = service.scorecard(1L);

		// @billing: 1 failing + 1 flaky; unassigned (null owner): 1 failing.
		assertThat(scores).anySatisfy((s) -> {
			assertThat(s.owner()).isEqualTo("@billing");
			assertThat(s.failing()).isEqualTo(1);
			assertThat(s.flaky()).isEqualTo(1);
		}).anySatisfy((s) -> {
			assertThat(s.owner()).isNull();
			assertThat(s.failing()).isEqualTo(1);
			assertThat(s.flaky()).isEqualTo(0);
		});
		// @billing sorts first (tie on failing, more flaky).
		assertThat(scores.get(0).owner()).isEqualTo("@billing");
	}

}
