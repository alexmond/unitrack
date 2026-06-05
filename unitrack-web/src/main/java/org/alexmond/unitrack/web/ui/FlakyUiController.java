package org.alexmond.unitrack.web.ui;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.FlakyStatus;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.report.FlakyTestService;
import org.alexmond.unitrack.report.ReportingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Server-rendered flaky-test dashboard and quarantine actions. */
@Controller
@RequiredArgsConstructor
public class FlakyUiController {

	private final FlakyTestService flaky;

	private final ReportingService reporting;

	@GetMapping("/projects/{id}/flaky")
	public String flaky(@PathVariable Long id, Model model) {
		Project project = reporting.findProject(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
		model.addAttribute("project", project);
		model.addAttribute("flaky", flaky.listFlaky(id));
		return "flaky";
	}

	@PostMapping("/projects/{id}/flaky/status")
	public String setStatus(@PathVariable Long id, @RequestParam String name,
			@RequestParam(required = false) String className, @RequestParam FlakyStatus status,
			@RequestParam(required = false) String note) {
		flaky.setStatus(id, className, name, status, note);
		return "redirect:/projects/" + id + "/flaky";
	}

}
