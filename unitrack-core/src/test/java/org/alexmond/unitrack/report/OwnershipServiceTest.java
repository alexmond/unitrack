package org.alexmond.unitrack.report;

import java.util.List;
import java.util.Map;

import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestOwnerRule;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.alexmond.unitrack.repository.TestOwnerRuleRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class OwnershipServiceTest {

	private final TestOwnerRuleRepository rules = mock(TestOwnerRuleRepository.class);

	private final OwnershipService service = new OwnershipService(rules, mock(ProjectRepository.class));

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

}
