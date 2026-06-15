package org.alexmond.unitrack.web.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.AuditEntry;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.report.ReportingService;
import org.alexmond.unitrack.web.account.AuditService;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Admin-only viewer for the append-only audit trail. Access is enforced in
 * {@code SecurityConfig} ({@code /audit/**} requires {@code ROLE_ADMIN}, even in open
 * mode).
 */
@Controller
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuditController {

	private static final int LIMIT = 200;

	private final AuditService audit;

	private final ReportingService reporting;

	@GetMapping("/audit")
	public String audit(@RequestParam(required = false) Long project, Model model) {
		List<AuditEntry> entries = (project != null) ? this.audit.recentForProject(project, LIMIT)
				: this.audit.recent(LIMIT);
		// Resolve project ids to names once for display (small set per page).
		Map<Long, String> projectNames = new LinkedHashMap<>();
		for (AuditEntry e : entries) {
			Long pid = e.getProjectId();
			if (pid != null && !projectNames.containsKey(pid)) {
				projectNames.put(pid, this.reporting.findProject(pid).map(Project::getName).orElse("#" + pid));
			}
		}
		model.addAttribute("entries", entries);
		model.addAttribute("projectNames", projectNames);
		model.addAttribute("filterProject", project);
		return "audit";
	}

}
