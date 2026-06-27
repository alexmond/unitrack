package org.alexmond.unitrack.web.ui;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.Visibility;
import org.alexmond.unitrack.report.ProjectSettingsService;
import org.alexmond.unitrack.report.ReportingService;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.alexmond.unitrack.web.account.MembershipService;
import org.alexmond.unitrack.web.github.GitHubProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Per-project quality-gate settings page (requires authentication). */
@Controller
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SettingsController {

	private final ReportingService reporting;

	private final ProjectSettingsService settings;

	private final GitHubProperties github;

	private final org.alexmond.unitrack.web.gitlab.GitLabProperties gitlab;

	private final MembershipService membership;

	private final ProjectRepository projects;

	@GetMapping("/projects/{id}/settings")
	public String settings(@PathVariable Long id, Model model, Authentication auth) {
		Project project = reporting.findProject(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
		// The settings page exposes project config (gate thresholds, GitHub/GitLab) —
		// viewing it
		// requires write access, matching the save handler. Otherwise any logged-in user
		// could read
		// another project's (incl. a private project's) settings.
		if (auth == null || !membership.canWrite(auth.getName(), id)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project write access required");
		}
		model.addAttribute("project", project);
		model.addAttribute("settings", settings.find(id).orElse(null));
		model.addAttribute("globals", settings.globals());
		model.addAttribute("github", github);
		model.addAttribute("gitlab", gitlab);
		return "settings";
	}

	@PostMapping("/projects/{id}/settings")
	public String save(@PathVariable Long id, @RequestParam(required = false) String baseBranch,
			@RequestParam(required = false) String minLineCoverage,
			@RequestParam(required = false) String maxCoverageDropPct,
			@RequestParam(required = false) String failOnNewFailures, @RequestParam(required = false) String ghEnabled,
			@RequestParam(required = false) String ghContext, @RequestParam(required = false) String ghPrComment,
			@RequestParam(required = false) String glEnabled, Authentication auth) {
		if (reporting.findProject(id).isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
		}
		if (auth == null || !membership.canWrite(auth.getName(), id)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project write access required");
		}
		settings.save(id, baseBranch, parseDouble(minLineCoverage), parseDouble(maxCoverageDropPct),
				parseBoolean(failOnNewFailures), parseBoolean(ghEnabled), ghContext, parseBoolean(ghPrComment),
				parseBoolean(glEnabled));
		return "redirect:/projects/" + id + "/settings";
	}

	/** Change project visibility (PUBLIC/PRIVATE). Owner-only. */
	@PostMapping("/projects/{id}/visibility")
	@Transactional
	public String visibility(@PathVariable Long id, @RequestParam Visibility visibility, Authentication auth) {
		Project project = reporting.findProject(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
		if (auth == null || !membership.canManage(auth.getName(), id)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project owner access required");
		}
		project.setVisibility(visibility);
		projects.save(project);
		return "redirect:/projects/" + id + "/settings";
	}

	private static Double parseDouble(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Double.valueOf(value.trim());
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	/** Tri-state: blank inherits the global default, otherwise true/false. */
	private static Boolean parseBoolean(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return "true".equalsIgnoreCase(value.trim());
	}

}
