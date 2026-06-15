package org.alexmond.unitrack.report;

import org.alexmond.unitrack.domain.TestOwnerRule;

/** API/UI view of a test-ownership rule. */
public record TestOwnerRuleView(Long id, String owner, String pattern, int priority) {

	public static TestOwnerRuleView of(TestOwnerRule r) {
		return new TestOwnerRuleView(r.getId(), r.getOwner(), r.getPattern(), r.getPriority());
	}
}
