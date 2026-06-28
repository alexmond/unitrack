package org.alexmond.unitrack.web.ui;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.web.ingest.IngestJobService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Admin-only ingest processing history (#368): every upload attempt with its outcome and,
 * on failure, the reason. Access is enforced in {@code SecurityConfig} ({@code /ingest}
 * requires {@code ROLE_ADMIN}, even in open mode — the list can name private projects).
 */
@Controller
@RequiredArgsConstructor
public class IngestHistoryController {

	private static final int LIMIT = 200;

	private final IngestJobService ingestJobs;

	@GetMapping("/ingest")
	public String ingest(Model model) {
		model.addAttribute("jobs", this.ingestJobs.recent(LIMIT));
		return "ingest";
	}

}
