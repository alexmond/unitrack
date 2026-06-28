package org.alexmond.unitrack.web.ui;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.web.account.ProjectAccessService;
import org.alexmond.unitrack.web.ingest.IngestJobService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Ingest processing history (#368): every upload attempt with its outcome and, on
 * failure, the reason. The cross-project view at {@code /ingest} is admin-only (it can
 * name private projects — enforced in {@code SecurityConfig}); the per-project view at
 * {@code /projects/{id}/ingest} lets a project's own owners/writers see their project's
 * ingest queue + history.
 */
@Controller
@RequiredArgsConstructor
public class IngestHistoryController {

	private static final int LIMIT = 200;

	private final IngestJobService ingestJobs;

	private final ProjectAccessService access;

	@GetMapping("/ingest")
	public String ingest(Model model) {
		model.addAttribute("jobs", this.ingestJobs.recent(LIMIT));
		return "ingest";
	}

	@GetMapping("/projects/{id}/ingest")
	public String projectIngest(@PathVariable Long id, Model model) {
		Project project = this.access.requireWriteProject(id);
		model.addAttribute("project", project);
		model.addAttribute("jobs", this.ingestJobs.recentForProject(project.getName(), LIMIT));
		return "project-ingest";
	}

}
