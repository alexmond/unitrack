package org.alexmond.unitrack.web.api;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.report.PerfRunRegression;
import org.alexmond.unitrack.report.PerfRunRegressionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Perf-run gate: latency/throughput/error-rate vs the baseline. Returns {@code 200} when
 * the run is within thresholds and {@code 422} when it regressed, so a CI step can fail
 * the build on the HTTP status (the JSON body carries the per-rule detail either way).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PerfRunRegressionController {

	private final PerfRunRegressionService perfRunRegression;

	@GetMapping("/perf-runs/{id}/regression")
	public ResponseEntity<PerfRunRegression> regression(@PathVariable Long id) {
		return this.perfRunRegression.evaluate(id)
			.map((r) -> ResponseEntity.status(r.passed() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY).body(r))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

}
