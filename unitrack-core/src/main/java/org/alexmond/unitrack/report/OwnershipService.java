package org.alexmond.unitrack.report;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestOwnerRule;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.alexmond.unitrack.repository.TestOwnerRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attributes tests to owners via {@link TestOwnerRule}s matched on the test class name.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OwnershipService {

	private final TestOwnerRuleRepository rules;

	private final ProjectRepository projects;

	public List<TestOwnerRuleView> listRules(Long projectId) {
		return rules.findByProjectIdOrderByPriorityAscIdAsc(projectId).stream().map(TestOwnerRuleView::of).toList();
	}

	/**
	 * The id of the project a rule belongs to (for authorization), or empty if it's gone.
	 */
	public Optional<Long> projectIdOfRule(Long ruleId) {
		return rules.findById(ruleId).map((r) -> r.getProject().getId());
	}

	@Transactional
	public TestOwnerRuleView addRule(Long projectId, String owner, String pattern, int priority) {
		Project project = projects.findById(projectId)
			.orElseThrow(() -> new IllegalArgumentException("Unknown project " + projectId));
		TestOwnerRule rule = rules.save(new TestOwnerRule(project, owner, pattern, priority));
		return TestOwnerRuleView.of(rule);
	}

	@Transactional
	public void deleteRule(Long ruleId) {
		rules.deleteById(ruleId);
	}

	/**
	 * Owner per case id for a set of cases (null when no rule matches the class name).
	 */
	public Map<Long, String> ownerByCaseId(Long projectId, List<TestCaseResult> caseList) {
		List<TestOwnerRule> ruleList = rules.findByProjectIdOrderByPriorityAscIdAsc(projectId);
		Map<Long, String> result = new LinkedHashMap<>();
		for (TestCaseResult c : caseList) {
			result.put(c.getId(), ownerFor(c.getClassName(), ruleList));
		}
		return result;
	}

	private static String ownerFor(String className, List<TestOwnerRule> ruleList) {
		if (className == null) {
			return null;
		}
		for (TestOwnerRule rule : ruleList) {
			if (matches(rule.getPattern(), className)) {
				return rule.getOwner();
			}
		}
		return null;
	}

	private static boolean matches(String pattern, String text) {
		try {
			return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text).find();
		}
		catch (PatternSyntaxException ex) {
			return text.toLowerCase(Locale.ROOT).contains(pattern.toLowerCase(Locale.ROOT));
		}
	}

}
