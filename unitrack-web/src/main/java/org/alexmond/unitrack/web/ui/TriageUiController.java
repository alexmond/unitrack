package org.alexmond.unitrack.web.ui;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.report.TriageService;
import org.alexmond.unitrack.web.account.ProjectAccessService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Server-rendered management of triage rules. */
@Controller
@RequiredArgsConstructor
public class TriageUiController {

	private final TriageService triage;

	private final ProjectAccessService access;

	@GetMapping("/projects/{id}/triage")
	public String rules(@PathVariable Long id, Model model) {
		Project project = access.requireReadProjectUi(id);
		model.addAttribute("project", project);
		model.addAttribute("rules", triage.listRules(id));
		return "triage";
	}

	@PostMapping("/projects/{id}/triage/rules")
	public String add(@PathVariable Long id, @RequestParam String name, @RequestParam String category,
			@RequestParam String pattern, @RequestParam(required = false) Integer priority) {
		access.requireWriteProject(id);
		triage.addRule(id, name, category, pattern, (priority != null) ? priority : 100);
		return "redirect:/projects/" + id + "/triage";
	}

	@PostMapping("/projects/{id}/triage/rules/{ruleId}/delete")
	public String delete(@PathVariable Long id, @PathVariable Long ruleId) {
		access.requireWriteProject(id);
		triage.deleteRule(ruleId);
		return "redirect:/projects/" + id + "/triage";
	}

}
