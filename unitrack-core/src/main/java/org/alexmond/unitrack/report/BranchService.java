package org.alexmond.unitrack.report;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.config.BranchProperties;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.repository.BranchRunCount;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Summarizes a project's branches by their latest run (Codecov-style branches list). The
 * runs are the source of truth — no separate branch entity — and the gate base branch is
 * marked as the default.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchService {

	private final TestRunRepository runs;

	private final ProjectSettingsService settings;

	private final BranchProperties branchProps;

	/** Branches for a project: default branch first, then most-recently-active first. */
	public List<BranchSummary> list(Long projectId) {
		String base = this.settings.gateConfig(projectId).baseBranch();
		// Two batch queries for the whole project instead of latest-run + count per
		// branch.
		Map<String, Long> countByBranch = new HashMap<>();
		for (BranchRunCount c : this.runs.countRunsPerBranch(projectId)) {
			countByBranch.put(c.getBranch(), c.getCnt());
		}
		return this.runs.findLatestRunPerBranch(projectId)
			.stream()
			.map((latest) -> summarize(latest, base, countByBranch.getOrDefault(latest.getBranch(), 0L)))
			.sorted(Comparator.comparing(BranchSummary::defaultBranch)
				.reversed()
				.thenComparing(BranchSummary::lastRunAt, Comparator.reverseOrder()))
			.toList();
	}

	private BranchSummary summarize(TestRun latest, String base, long runCount) {
		String branch = latest.getBranch();
		boolean defaultBranch = branch.equals(base);
		boolean shown = defaultBranch || isProtected(branch) || isActive(latest.getCreatedAt());
		return new BranchSummary(branch, latest.getStatus(), latest.passRate(), latest.getLineCoveragePct(),
				latest.getId(), latest.getCreatedAt(), runCount, defaultBranch, shown);
	}

	private boolean isProtected(String branch) {
		return this.branchProps.getProtectedPatterns().stream().anyMatch((pattern) -> globMatches(pattern, branch));
	}

	private boolean isActive(Instant lastRunAt) {
		return lastRunAt != null
				&& lastRunAt.isAfter(Instant.now().minus(this.branchProps.getActiveDays(), ChronoUnit.DAYS));
	}

	/**
	 * Whether {@code branch} matches a glob {@code pattern} ({@code *} = any run of
	 * characters).
	 */
	static boolean globMatches(String pattern, String branch) {
		String regex = Pattern.quote(pattern).replace("*", "\\E.*\\Q");
		return branch.matches(regex);
	}

}
