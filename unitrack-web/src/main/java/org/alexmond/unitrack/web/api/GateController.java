package org.alexmond.unitrack.web.api;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.report.QualityGateResult;
import org.alexmond.unitrack.report.QualityGateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the quality-gate evaluation for a run. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GateController {

	private final QualityGateService gate;

	@GetMapping("/runs/{id}/quality-gate")
	public ResponseEntity<QualityGateResult> qualityGate(@PathVariable Long id) {
		return gate.evaluate(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

}
