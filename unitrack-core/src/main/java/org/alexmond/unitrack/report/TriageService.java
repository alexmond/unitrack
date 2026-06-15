package org.alexmond.unitrack.report;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.TestStatus;
import org.alexmond.unitrack.domain.TriageRule;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.alexmond.unitrack.repository.TestCaseResultRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.alexmond.unitrack.repository.TriageRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Categorizes failures using user-defined {@link TriageRule}s. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TriageService {

	private static final List<TestStatus> FAILED = List.of(TestStatus.FAILED, TestStatus.ERROR);

	static final String UNTRIAGED = "untriaged";

	private final TriageRuleRepository rules;

	private final ProjectRepository projects;

	private final TestRunRepository runs;

	private final TestCaseResultRepository cases;

	public List<TriageRuleView> listRules(Long projectId) {
		return rules.findByProjectIdOrderByPriorityAscIdAsc(projectId).stream().map(TriageRuleView::of).toList();
	}

	/**
	 * The id of the project a rule belongs to (for authorization), or empty if it's gone.
	 */
	public java.util.Optional<Long> projectIdOfRule(Long ruleId) {
		return rules.findById(ruleId).map((r) -> r.getProject().getId());
	}

	@Transactional
	public TriageRuleView addRule(Long projectId, String name, String category, String pattern, int priority) {
		Project project = projects.findById(projectId)
			.orElseThrow(() -> new IllegalArgumentException("Unknown project " + projectId));
		TriageRule rule = rules.save(new TriageRule(project, name, category, pattern, priority));
		return TriageRuleView.of(rule);
	}

	@Transactional
	public void deleteRule(Long ruleId) {
		rules.deleteById(ruleId);
	}

	/** Categorizes a run's failures and counts categories. */
	public TriageResult triageRun(Long runId) {
		TestRun run = runs.findById(runId).orElse(null);
		if (run == null) {
			return new TriageResult(List.of(), List.of());
		}
		List<TriageRule> ruleList = rules.findByProjectIdOrderByPriorityAscIdAsc(run.getProject().getId());
		List<TestCaseResult> failures = cases.findByRunIdAndStatusInOrderByClassNameAscNameAsc(runId, FAILED);

		Map<String, Integer> counts = new LinkedHashMap<>();
		List<TriageResult.CategorizedCase> categorized = failures.stream().map((c) -> {
			String category = categorize(c, ruleList);
			counts.merge(category, 1, Integer::sum);
			return new TriageResult.CategorizedCase(testName(c), c.getStatus().name(), category);
		}).toList();

		List<TriageResult.CategoryCount> summary = counts.entrySet()
			.stream()
			.map((e) -> new TriageResult.CategoryCount(e.getKey(), e.getValue()))
			.toList();
		return new TriageResult(categorized, summary);
	}

	/** Category per case id for a set of cases (for the run page). */
	public Map<Long, String> categoryByCaseId(Long projectId, List<TestCaseResult> caseList) {
		List<TriageRule> ruleList = rules.findByProjectIdOrderByPriorityAscIdAsc(projectId);
		Map<Long, String> result = new LinkedHashMap<>();
		for (TestCaseResult c : caseList) {
			result.put(c.getId(), categorize(c, ruleList));
		}
		return result;
	}

	private String categorize(TestCaseResult c, List<TriageRule> ruleList) {
		String text = failureText(c);
		for (TriageRule rule : ruleList) {
			if (rule.isEnabled() && matches(rule.getPattern(), text)) {
				return rule.getCategory();
			}
		}
		return UNTRIAGED;
	}

	private static boolean matches(String pattern, String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		try {
			return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(text).find();
		}
		catch (PatternSyntaxException ex) {
			return text.toLowerCase(Locale.ROOT).contains(pattern.toLowerCase(Locale.ROOT));
		}
	}

	private static String failureText(TestCaseResult c) {
		StringBuilder sb = new StringBuilder();
		if (c.getFailureType() != null) {
			sb.append(c.getFailureType()).append('\n');
		}
		if (c.getFailureMessage() != null) {
			sb.append(c.getFailureMessage()).append('\n');
		}
		if (c.getFailureStacktrace() != null) {
			sb.append(c.getFailureStacktrace());
		}
		return sb.toString();
	}

	private static String testName(TestCaseResult c) {
		String cls = (c.getClassName() != null) ? c.getClassName() : "";
		return cls.isBlank() ? c.getName() : cls + "#" + c.getName();
	}

}
