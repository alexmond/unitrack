package org.alexmond.unitrack.web.ops;

import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.alexmond.unitrack.web.live.LiveEventService;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Operational dashboard ({@code /ops}): one glance at totals, pass/fail breakdown, live
 * SSE subscribers, and recent failures. Admin-only (enforced in {@code SecurityConfig}).
 * Everything is mapped to DTOs inside the read-only transaction so the template touches
 * no lazy entities; the stats fragment is polled by HTMX for a live refresh.
 */
@Controller
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OpsController {

	private static final String PASSED = "PASSED";

	private final ProjectRepository projects;

	private final TestRunRepository runs;

	private final LiveEventService liveEvents;

	@GetMapping("/ops")
	public String ops(Model model) {
		populate(model);
		return "ops";
	}

	/** The live-refreshing fragment (HTMX polls this and swaps it in). */
	@GetMapping("/ops/stats")
	public String stats(Model model) {
		populate(model);
		return "ops :: stats";
	}

	private void populate(Model model) {
		long totalRuns = this.runs.count();
		long passed = this.runs.countByStatus(PASSED);
		long failed = totalRuns - passed;
		double passRate = (totalRuns > 0) ? (passed * 100.0) / totalRuns : 0.0;
		List<FailedRun> failures = this.runs.findTop20ByStatusNotOrderByCreatedAtDesc(PASSED)
			.stream()
			.map(OpsController::toFailedRun)
			.toList();
		model.addAttribute("stats", new OpsStats(this.projects.count(), totalRuns, passed, failed, passRate,
				this.liveEvents.subscriberCount()));
		model.addAttribute("failures", failures);
	}

	private static FailedRun toFailedRun(TestRun r) {
		return new FailedRun(r.getId(), r.getProject().getName(), r.getBranch(), r.getShortSha(), r.getStatus(),
				r.getCreatedAt());
	}

	/** Headline operational numbers. */
	public record OpsStats(long projects, long runs, long passed, long failed, double passRate, int subscribers) {
	}

	/** One recent failing run, with the bits the page links/labels. */
	public record FailedRun(Long runId, String project, String branch, String shortSha, String status,
			Instant createdAt) {
	}

}
