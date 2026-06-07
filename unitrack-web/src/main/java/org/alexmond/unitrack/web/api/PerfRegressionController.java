package org.alexmond.unitrack.web.api;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.report.PerfRegressionResult;
import org.alexmond.unitrack.report.PerfRegressionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes slow-test (duration) regressions for a run vs its baseline. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PerfRegressionController {

	private final PerfRegressionService perfRegression;

	@GetMapping("/runs/{id}/perf-regression")
	public ResponseEntity<PerfRegressionResult> perfRegression(@PathVariable Long id) {
		return this.perfRegression.diff(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

}
