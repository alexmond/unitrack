package org.alexmond.unitrack.report;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.config.BranchProperties;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Removes branches that no longer exist. Branches are derived from run history (no branch
 * entity), so "deleting a branch" means hard-deleting its runs — after which it drops out
 * of the branches list, trends and the board.
 *
 * <p>
 * Two triggers: an SCM branch-delete webhook ({@link #deleteBranch}, immediate and
 * precise) and a time-based expiry ({@link #expireStaleBranches}, the polyglot backstop
 * that catches abandoned branches from any uploader). Protected and default branches are
 * never removed by either path.
 */
@Service
@RequiredArgsConstructor
public class BranchCleanupService {

	private static final Logger log = LoggerFactory.getLogger(BranchCleanupService.class);

	private final ProjectRepository projects;

	private final TestRunRepository runs;

	private final ProjectSettingsService settings;

	private final BranchProperties branchProps;

	private final RunDeletionService runDeletion;

	/**
	 * Hard-deletes every run on {@code branch} of a project — for an SCM branch-delete
	 * event. Protected/default branches are skipped (returns -1); otherwise returns the
	 * number of runs deleted.
	 */
	public int deleteBranch(Long projectId, String branch) {
		if (branch == null || branch.isBlank()) {
			return 0;
		}
		if (isProtected(branch) || branch.equals(baseBranch(projectId))) {
			log.info("Ignoring branch-delete for protected/default branch '{}' on project {}", branch, projectId);
			return -1;
		}
		int deleted = purge(projectId, branch, null);
		if (deleted > 0) {
			log.info("Removed branch '{}' on project {} ({} run(s))", branch, projectId, deleted);
		}
		return deleted;
	}

	/**
	 * Daily expiry: hard-deletes non-protected branches whose latest run is older than
	 * the configured retention. No-op when retention is disabled (0). Returns the total
	 * runs deleted.
	 */
	public int expireStaleBranches(Instant now) {
		int retain = this.branchProps.getRetainDays();
		if (retain <= 0) {
			return 0;
		}
		Instant cutoff = now.minus(retain, ChronoUnit.DAYS);
		int deleted = 0;
		for (Project project : this.projects.findAll()) {
			Long projectId = project.getId();
			Long keep = this.runs.findLatestRunId(projectId);
			String base = baseBranch(projectId);
			for (String branch : this.runs.findDistinctBranches(projectId)) {
				if (isProtected(branch) || branch.equals(base)) {
					continue;
				}
				TestRun latest = this.runs.findLatestByBranch(projectId, branch, null, PageRequest.ofSize(1))
					.stream()
					.findFirst()
					.orElse(null);
				if (latest == null || latest.getCreatedAt() == null || latest.getCreatedAt().isAfter(cutoff)) {
					continue;
				}
				deleted += purge(projectId, branch, keep);
			}
		}
		if (deleted > 0) {
			log.info("Branch expiry removed {} run(s) past the {}-day retention", deleted, retain);
		}
		return deleted;
	}

	/**
	 * Deletes each run of a branch except {@code keepRunId} (the project's latest, a
	 * safety).
	 */
	private int purge(Long projectId, String branch, Long keepRunId) {
		int deleted = 0;
		for (Long runId : this.runs.findIdsByProjectIdAndBranch(projectId, branch)) {
			if (runId.equals(keepRunId)) {
				continue;
			}
			this.runDeletion.deleteRun(runId);
			deleted++;
		}
		return deleted;
	}

	private String baseBranch(Long projectId) {
		return this.settings.gateConfig(projectId).baseBranch();
	}

	private boolean isProtected(String branch) {
		return this.branchProps.getProtectedPatterns().stream().anyMatch((p) -> BranchService.globMatches(p, branch));
	}

}
