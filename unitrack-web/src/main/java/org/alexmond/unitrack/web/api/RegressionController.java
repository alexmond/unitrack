package org.alexmond.unitrack.web.api;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.report.TestRegressionResult;
import org.alexmond.unitrack.report.TestRegressionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the test-regression diff (new failures / new passes vs baseline) for a run. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RegressionController {

	private final TestRegressionService regression;

	@GetMapping("/runs/{id}/regression")
	public ResponseEntity<TestRegressionResult> regression(@PathVariable Long id) {
		return this.regression.diff(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

}
