package org.alexmond.unitrack.web.account;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.PerfRun;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.ReportingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Enforces project visibility/membership at read and write boundaries, throwing the
 * appropriate HTTP status. Reads on a project the caller can't see return 404 (so a
 * private project's existence isn't leaked); writes without permission return 403.
 * Resolution helpers (by project id, run id, perf-run id) centralize the check so each
 * controller is a one-liner.
 */
@Service
@RequiredArgsConstructor
public class ProjectAccessService {

	private final MembershipService membership;

	private final ReportingService reporting;

	/** The current authenticated username, or null when anonymous. */
	public String currentUsername() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
			return null;
		}
		return auth.getName();
	}

	/** Whether the current caller may read the project. */
	public boolean canRead(Project project) {
		return membership.canRead(currentUsername(), project);
	}

	/**
	 * Returns the project if the caller may read it, else 404 (hides private projects).
	 */
	public Project requireRead(Project project) {
		if (!membership.canRead(currentUsername(), project)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
		}
		return project;
	}

	/** Loads + read-checks a project by id (404 if missing or not readable). */
	public Project requireReadProject(Long projectId) {
		return requireRead(reporting.findProject(projectId).orElseThrow(this::notFound));
	}

	/**
	 * Loads + read-checks a test run by id via its project (404 if missing or not
	 * readable).
	 */
	public TestRun requireReadRun(Long runId) {
		TestRun run = reporting.findRun(runId).orElseThrow(this::notFound);
		requireRead(run.getProject());
		return run;
	}

	/**
	 * Loads + read-checks a perf run by id via its project (404 if missing or not
	 * readable).
	 */
	public PerfRun requireReadPerfRun(Long perfRunId) {
		PerfRun run = reporting.findPerfRun(perfRunId).orElseThrow(this::notFound);
		requireRead(run.getProject());
		return run;
	}

	/** Throws 403 unless the caller may write to the project (WRITE+ or admin). */
	public void requireWrite(Project project) {
		String username = currentUsername();
		if (username == null || !membership.canWrite(username, project.getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Write access required");
		}
	}

	/** Loads a project by id (404) then write-checks it (403). */
	public Project requireWriteProject(Long projectId) {
		Project project = reporting.findProject(projectId).orElseThrow(this::notFound);
		requireWrite(project);
		return project;
	}

	private ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
	}

}
