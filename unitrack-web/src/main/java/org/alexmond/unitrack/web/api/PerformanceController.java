package org.alexmond.unitrack.web.api;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.report.PerformanceService;
import org.alexmond.unitrack.report.PerformanceSummary;
import org.alexmond.unitrack.report.TestDurationTrend;
import org.alexmond.unitrack.web.account.ProjectAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only JSON endpoints for unit-test performance (slowest tests + duration trends).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PerformanceController {

	private static final int SLOW_LIMIT = 20;

	private static final int TREND_LIMIT = 30;

	private final PerformanceService performance;

	private final ProjectAccessService access;

	@GetMapping("/projects/{id}/performance")
	public ResponseEntity<PerformanceSummary> performance(@PathVariable Long id) {
		this.access.requireReadProject(id);
		return ResponseEntity.ok(this.performance.summary(id, SLOW_LIMIT, TREND_LIMIT));
	}

	@GetMapping("/projects/{id}/test-duration")
	public ResponseEntity<TestDurationTrend> testDuration(@PathVariable Long id,
			@RequestParam(name = "className", defaultValue = "") String className, @RequestParam String name) {
		this.access.requireReadProject(id);
		return ResponseEntity.ok(this.performance.testDurationTrend(id, className, name, TREND_LIMIT));
	}

}
