package org.alexmond.unitrack.report;

import org.alexmond.unitrack.domain.TriageRule;

/** API/UI view of a triage rule. */
public record TriageRuleView(Long id, String name, String category, String pattern, int priority, boolean enabled) {

	public static TriageRuleView of(TriageRule r) {
		return new TriageRuleView(r.getId(), r.getName(), r.getCategory(), r.getPattern(), r.getPriority(),
				r.isEnabled());
	}
}
