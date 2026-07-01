package org.alexmond.unitrack.web.ui;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.FlakyStatus;
import org.alexmond.unitrack.report.FlakyTestService;
import org.alexmond.unitrack.web.account.ProjectAccessService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Server-rendered flaky-test dashboard and quarantine actions. */
@Controller
@RequiredArgsConstructor
public class FlakyUiController {

	private final FlakyTestService flaky;

	private final ProjectAccessService access;

	/**
	 * The Flaky tab is folded into Tests (epic #390) — keep the URL working for
	 * bookmarks.
	 */
	@GetMapping("/projects/{id}/flaky")
	public String flaky(@PathVariable Long id) {
		return "redirect:/projects/" + id + "/tests#flaky-section";
	}

	@PostMapping("/projects/{id}/flaky/status")
	public String setStatus(@PathVariable Long id, @RequestParam String name,
			@RequestParam(required = false) String className, @RequestParam FlakyStatus status,
			@RequestParam(required = false) String note) {
		access.requireWriteProject(id);
		flaky.setStatus(id, className, name, status, note);
		return "redirect:/projects/" + id + "/tests#flaky-section";
	}

}
