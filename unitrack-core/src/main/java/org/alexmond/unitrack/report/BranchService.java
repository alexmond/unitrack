package org.alexmond.unitrack.report;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.config.BranchProperties;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.springframework.data.domain.PageRequest;
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
		return this.runs.findDistinctBranches(projectId)
			.stream()
			.map((branch) -> summarize(projectId, branch, base))
			.filter(Objects::nonNull)
			.sorted(Comparator.comparing(BranchSummary::defaultBranch)
				.reversed()
				.thenComparing(BranchSummary::lastRunAt, Comparator.reverseOrder()))
			.toList();
	}

	private BranchSummary summarize(Long projectId, String branch, String base) {
		TestRun latest = this.runs.findLatestByBranch(projectId, branch, null, PageRequest.ofSize(1))
			.stream()
			.findFirst()
			.orElse(null);
		if (latest == null) {
			return null;
		}
		boolean defaultBranch = branch.equals(base);
		boolean shown = defaultBranch || isProtected(branch) || isActive(latest.getCreatedAt());
		return new BranchSummary(branch, latest.getStatus(), latest.passRate(), latest.getLineCoveragePct(),
				latest.getId(), latest.getCreatedAt(), this.runs.countByProjectIdAndBranch(projectId, branch),
				defaultBranch, shown);
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
