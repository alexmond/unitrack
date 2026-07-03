package org.alexmond.unitrack.web.ui;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.report.ProjectHealth;
import org.alexmond.unitrack.report.ProjectHealthService;
import org.alexmond.unitrack.web.account.MembershipService;
import org.alexmond.unitrack.web.account.ProjectAccessService;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the login-gated "New summary" page: a cross-project rollup of gate status,
 * pass%, coverage, flaky tests and red-streaks, restricted to the projects the signed-in
 * user may read. Reuses the same board + KPI assembly as the home dashboard, so the
 * numbers match. The route is forced to require authentication in {@code SecurityConfig}
 * (even in open mode, like {@code /profile}); its nav link renders only for signed-in
 * users.
 */
@Controller
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NewSummaryController {

	private final ProjectHealthService projectHealth;

	private final ProjectAccessService access;

	private final MembershipService membership;

	@GetMapping("/new-summary")
	String newSummary(Model model) {
		String user = this.access.currentUsername();
		List<ProjectHealth> board = this.projectHealth.board(this.membership.readableBy(user));
		model.addAttribute("board", board);
		model.addAttribute("summary", ProjectHealthService.summarize(board));
		// Extra rollups the KPI-strip summarize() doesn't carry — cheap in-memory counts.
		model.addAttribute("passingGates",
				board.stream().filter(ProjectHealth::hasRuns).filter(ProjectHealth::gatePassed).count());
		model.addAttribute("regressedCount", board.stream().filter(ProjectHealth::isRegressed).count());
		model.addAttribute("noRunsCount", board.stream().filter((h) -> !h.hasRuns()).count());
		return "new-summary";
	}

}
