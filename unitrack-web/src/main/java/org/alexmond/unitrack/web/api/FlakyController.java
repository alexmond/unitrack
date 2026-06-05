package org.alexmond.unitrack.web.api;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.FlakyStatus;
import org.alexmond.unitrack.report.FlakyTestService;
import org.alexmond.unitrack.report.FlakyTestView;
import org.alexmond.unitrack.report.ReportingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for flaky-test detection and quarantine. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FlakyController {

	private final FlakyTestService flaky;

	private final ReportingService reporting;

	@GetMapping("/projects/{id}/flaky")
	public ResponseEntity<List<FlakyTestView>> flaky(@PathVariable Long id) {
		if (reporting.findProject(id).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(flaky.listFlaky(id));
	}

	@PostMapping("/projects/{id}/flaky/status")
	public ResponseEntity<FlakyTestView> setStatus(@PathVariable Long id, @RequestBody StatusRequest req) {
		if (reporting.findProject(id).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		flaky.setStatus(id, req.className(), req.name(), req.status(), req.note());
		return ResponseEntity.accepted().build();
	}

	/** Body for changing a flaky test's quarantine state. */
	public record StatusRequest(String className, String name, FlakyStatus status, String note) {
	}

}
