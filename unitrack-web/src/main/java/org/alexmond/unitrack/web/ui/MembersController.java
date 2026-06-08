package org.alexmond.unitrack.web.ui;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.ProjectRole;
import org.alexmond.unitrack.report.ReportingService;
import org.alexmond.unitrack.web.account.MembershipService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Project members admin page — invite / change role / remove. Owner- or admin-only. */
@Controller
@RequiredArgsConstructor
public class MembersController {

	private final ReportingService reporting;

	private final MembershipService membership;

	@GetMapping("/projects/{id}/members")
	public String members(@PathVariable Long id, Authentication auth, Model model) {
		Project project = requireProjectAndManage(id, auth);
		model.addAttribute("project", project);
		model.addAttribute("members", membership.members(id));
		model.addAttribute("roles", ProjectRole.values());
		return "members";
	}

	@PostMapping("/projects/{id}/members")
	public String add(@PathVariable Long id, @RequestParam String username, @RequestParam ProjectRole role,
			Authentication auth) {
		requireProjectAndManage(id, auth);
		try {
			membership.addOrUpdate(id, username.trim(), role);
		}
		catch (IllegalArgumentException ex) {
			return "redirect:/projects/" + id + "/members?error=nouser";
		}
		return "redirect:/projects/" + id + "/members";
	}

	@PostMapping("/projects/{id}/members/{membershipId}/remove")
	public String remove(@PathVariable Long id, @PathVariable Long membershipId, Authentication auth) {
		requireProjectAndManage(id, auth);
		membership.remove(membershipId);
		return "redirect:/projects/" + id + "/members";
	}

	private Project requireProjectAndManage(Long id, Authentication auth) {
		Project project = reporting.findProject(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
		if (auth == null || !membership.canManage(auth.getName(), id)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project owner or admin required");
		}
		return project;
	}

}
