package org.alexmond.unitrack.web.api;

import org.alexmond.unitrack.domain.CoverageReport;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.ReportingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/** Read-only JSON endpoints for projects and runs. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class QueryController {

	private final ReportingService reporting;

	@GetMapping("/projects")
	public List<ApiResponses.ProjectJson> projects() {
		return reporting.listProjects()
			.stream()
			.map((p) -> ApiResponses.ProjectJson.of(p, reporting.runCount(p.getId())))
			.toList();
	}

	@GetMapping("/projects/{id}")
	public ResponseEntity<ApiResponses.ProjectJson> project(@PathVariable Long id) {
		return reporting.findProject(id)
			.map((p) -> ResponseEntity.ok(ApiResponses.ProjectJson.of(p, reporting.runCount(id))))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/projects/{id}/runs")
	public ResponseEntity<List<ApiResponses.RunJson>> runs(@PathVariable Long id,
			@RequestParam(defaultValue = "50") int limit) {
		Optional<Project> project = reporting.findProject(id);
		if (project.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		List<ApiResponses.RunJson> runs = reporting.recentRuns(id, clamp(limit))
			.stream()
			.map(ApiResponses.RunJson::of)
			.toList();
		return ResponseEntity.ok(runs);
	}

	@GetMapping("/runs/{id}")
	public ResponseEntity<ApiResponses.RunDetailJson> run(@PathVariable Long id) {
		Optional<TestRun> run = reporting.findRun(id);
		if (run.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		List<ApiResponses.SuiteJson> suites = reporting.suitesFor(id).stream().map(ApiResponses.SuiteJson::of).toList();
		List<ApiResponses.CaseJson> failures = reporting.failedCasesFor(id)
			.stream()
			.map(ApiResponses.CaseJson::of)
			.toList();
		ApiResponses.CoverageJson coverage = reporting.coverageFor(id).map(this::toCoverageJson).orElse(null);
		return ResponseEntity
			.ok(new ApiResponses.RunDetailJson(ApiResponses.RunJson.of(run.get()), suites, failures, coverage));
	}

	private ApiResponses.CoverageJson toCoverageJson(CoverageReport report) {
		return ApiResponses.CoverageJson.of(report, reporting.coverageFiles(report.getId(), 500));
	}

	private static int clamp(int limit) {
		return Math.max(1, Math.min(limit, 500));
	}

}
