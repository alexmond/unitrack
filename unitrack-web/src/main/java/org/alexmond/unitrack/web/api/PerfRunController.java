package org.alexmond.unitrack.web.api;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.report.PerfRunDetail;
import org.alexmond.unitrack.report.PerfRunDetailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Perf-run detail: summary metrics, per-label rows with baseline deltas, and the gate
 * verdict.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PerfRunController {

	private final PerfRunDetailService perfRunDetail;

	@GetMapping("/perf-runs/{id}")
	public ResponseEntity<PerfRunDetail> perfRun(@PathVariable Long id) {
		return this.perfRunDetail.detail(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

}
