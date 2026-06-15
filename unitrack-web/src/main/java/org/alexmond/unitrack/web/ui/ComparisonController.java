package org.alexmond.unitrack.web.ui;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.ComparisonService;
import org.alexmond.unitrack.report.RunComparison;
import org.alexmond.unitrack.web.account.ProjectAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Side-by-side diff of two runs (newly-failing / fixed / still-failing + deltas). */
@Controller
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ComparisonController {

	private final ComparisonService comparison;

	private final ProjectAccessService access;

	@GetMapping("/compare")
	public String compare(@RequestParam Long base, @RequestParam Long head, Model model) {
		// Both runs must be readable (404 hides private/unknown runs).
		access.requireReadRun(base);
		TestRun headRun = access.requireReadRun(head);
		RunComparison cmp = comparison.compare(base, head)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
		model.addAttribute("cmp", cmp);
		model.addAttribute("project", headRun.getProject());
		return "compare";
	}

}
