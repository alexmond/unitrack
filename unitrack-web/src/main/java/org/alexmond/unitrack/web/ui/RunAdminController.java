package org.alexmond.unitrack.web.ui;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.RunDeletionService;
import org.alexmond.unitrack.web.account.AuditService;
import org.alexmond.unitrack.web.account.ProjectAccessService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Owner action to purge a run — e.g. a bad upload (a partial rollup mis-tagged as
 * {@code default}) that skews coverage trends. Write access required; the deletion is
 * audited. Not on {@code DashboardController} because that class is read-only
 * transactional.
 */
@Controller
@RequiredArgsConstructor
public class RunAdminController {

	private final ProjectAccessService access;

	private final RunDeletionService runDeletion;

	private final AuditService audit;

	@PostMapping("/runs/{id}/delete")
	public String delete(@PathVariable Long id) {
		TestRun run = this.access.requireReadRun(id);
		this.access.requireWrite(run.getProject());
		Long projectId = run.getProject().getId();
		String detail = "run #" + id + " (" + ((run.getShortSha() != null) ? run.getShortSha() : "?") + ", flag "
				+ run.getFlag() + ")";
		this.runDeletion.deleteRun(id);
		this.audit.record(this.access.currentUsername(), "RUN_DELETE", "ui", projectId, detail);
		return "redirect:/projects/" + projectId;
	}

}
