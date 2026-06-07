package org.alexmond.unitrack.web.api;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.report.BlameEntry;
import org.alexmond.unitrack.report.BlameService;
import org.alexmond.unitrack.report.ReportingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** For each of a run's failing tests, where its failing streak first began (blame). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BlameController {

	private final BlameService blame;

	private final ReportingService reporting;

	@GetMapping("/runs/{id}/blame")
	public ResponseEntity<List<BlameEntry>> blame(@PathVariable Long id) {
		if (this.reporting.findRun(id).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(this.blame.blame(id));
	}

}
