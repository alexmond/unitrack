package org.alexmond.unitrack.report;

import java.util.ArrayList;
import java.util.Comparator;
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
import org.alexmond.unitrack.domain.TestRun;
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

	private final ReportingService reporting;

	private final FlakyTestService flaky;

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

	/**
	 * Per-owner accountability for a project: failing tests in the latest run + flaky
	 * tests, counted by owner (an "unassigned" bucket captures tests no rule matched).
	 * Sorted by failing then flaky, descending.
	 */
	public List<OwnerScore> scorecard(Long projectId) {
		List<TestOwnerRule> ruleList = rules.findByProjectIdOrderByPriorityAscIdAsc(projectId);
		Map<String, long[]> agg = new LinkedHashMap<>();
		List<TestRun> recent = reporting.recentRuns(projectId, 1);
		if (!recent.isEmpty()) {
			for (TestCaseResult c : reporting.failedCasesFor(recent.get(0).getId())) {
				agg.computeIfAbsent(ownerFor(c.getClassName(), ruleList), (k) -> new long[2])[0]++;
			}
		}
		for (FlakyTestView f : flaky.listFlaky(projectId)) {
			agg.computeIfAbsent(ownerFor(f.className(), ruleList), (k) -> new long[2])[1]++;
		}
		List<OwnerScore> scores = new ArrayList<>();
		agg.forEach((owner, counts) -> scores.add(new OwnerScore(owner, counts[0], counts[1])));
		scores.sort(Comparator.comparingLong(OwnerScore::failing).thenComparingLong(OwnerScore::flaky).reversed());
		return scores;
	}

	/**
	 * Cross-project owner accountability: each owner's failing + flaky counts summed over
	 * the given projects (typically the ones the caller may read), sorted failing then
	 * flaky descending — the global "who carries the failure/flaky debt" board.
	 */
	public List<OwnerScore> globalScorecard(List<Long> projectIds) {
		Map<String, long[]> agg = new LinkedHashMap<>();
		for (Long projectId : projectIds) {
			for (OwnerScore s : scorecard(projectId)) {
				long[] a = agg.computeIfAbsent(s.owner(), (k) -> new long[2]);
				a[0] += s.failing();
				a[1] += s.flaky();
			}
		}
		List<OwnerScore> scores = new ArrayList<>();
		agg.forEach((owner, counts) -> scores.add(new OwnerScore(owner, counts[0], counts[1])));
		scores.sort(Comparator.comparingLong(OwnerScore::failing).thenComparingLong(OwnerScore::flaky).reversed());
		return scores;
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

	/**
	 * One owner's accountability counts; {@code owner} is null for the unassigned bucket.
	 */
	public record OwnerScore(String owner, long failing, long flaky) {
	}

}
