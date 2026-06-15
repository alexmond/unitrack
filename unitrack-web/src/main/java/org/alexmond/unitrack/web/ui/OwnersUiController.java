package org.alexmond.unitrack.web.ui;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.report.OwnershipService;
import org.alexmond.unitrack.web.account.ProjectAccessService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Server-rendered management of test-ownership rules. */
@Controller
@RequiredArgsConstructor
public class OwnersUiController {

	private final OwnershipService ownership;

	private final ProjectAccessService access;

	@GetMapping("/projects/{id}/owners")
	public String rules(@PathVariable Long id, Model model) {
		Project project = access.requireReadProject(id);
		model.addAttribute("project", project);
		model.addAttribute("rules", ownership.listRules(id));
		model.addAttribute("scorecard", ownership.scorecard(id));
		return "owners";
	}

	@PostMapping("/projects/{id}/owners/rules")
	public String add(@PathVariable Long id, @RequestParam String owner, @RequestParam String pattern,
			@RequestParam(required = false) Integer priority) {
		access.requireWriteProject(id);
		ownership.addRule(id, owner, pattern, (priority != null) ? priority : 100);
		return "redirect:/projects/" + id + "/owners";
	}

	@PostMapping("/projects/{id}/owners/rules/{ruleId}/delete")
	public String delete(@PathVariable Long id, @PathVariable Long ruleId) {
		access.requireWriteProject(id);
		ownership.deleteRule(ruleId);
		return "redirect:/projects/" + id + "/owners";
	}

}
