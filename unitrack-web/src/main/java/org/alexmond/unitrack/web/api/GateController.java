package org.alexmond.unitrack.web.api;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.QualityGateResult;
import org.alexmond.unitrack.report.QualityGateService;
import org.alexmond.unitrack.report.ReportingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the quality-gate evaluation for a run, and a CI-friendly lookup by
 * commit/branch.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GateController {

	private final QualityGateService gate;

	private final ReportingService reporting;

	@GetMapping("/runs/{id}/quality-gate")
	public ResponseEntity<QualityGateResult> qualityGate(@PathVariable Long id) {
		return this.gate.evaluate(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	/**
	 * CI gate lookup by {@code project} name + {@code commit} (or {@code branch}), so a
	 * pipeline can fail a build on the verdict without knowing the internal run id.
	 * Returns the latest matching run's gate verdict; {@code 404} if no run matches,
	 * {@code 400} if neither commit nor branch is given.
	 */
	@GetMapping("/gate")
	public ResponseEntity<ApiResponses.GateReportJson> gate(@RequestParam String project,
			@RequestParam(required = false) String commit, @RequestParam(required = false) String branch,
			@RequestParam(required = false) String flag) {
		if (commit == null && branch == null) {
			return ResponseEntity.badRequest().build();
		}
		Optional<Project> found = this.reporting.findProjectByName(project);
		if (found.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		Long projectId = found.get().getId();
		Optional<TestRun> run = (commit != null) ? this.reporting.latestRunByCommit(projectId, commit, flag)
				: this.reporting.latestRunByBranch(projectId, branch, flag);
		return run.map(this::toReport).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	private ApiResponses.GateReportJson toReport(TestRun run) {
		QualityGateResult result = this.gate.evaluate(run.getId()).orElseThrow();
		Double delta = this.gate.coverageDelta(run.getId()).orElse(null);
		return ApiResponses.GateReportJson.of(run, result, delta);
	}

}
