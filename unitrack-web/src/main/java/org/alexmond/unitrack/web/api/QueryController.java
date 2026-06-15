package org.alexmond.unitrack.web.api;

import org.alexmond.unitrack.domain.CoverageReport;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.FailureCluster;
import org.alexmond.unitrack.report.FailureClusteringService;
import org.alexmond.unitrack.report.FlagSummary;
import org.alexmond.unitrack.report.PullRequestService;
import org.alexmond.unitrack.report.PullRequestSummary;
import org.alexmond.unitrack.report.ReportingService;
import org.alexmond.unitrack.web.account.MembershipService;
import org.alexmond.unitrack.web.account.ProjectAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only JSON endpoints for projects and runs. Honors project visibility. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class QueryController {

	private final ReportingService reporting;

	private final FailureClusteringService clustering;

	private final PullRequestService pullRequests;

	private final ProjectAccessService access;

	private final MembershipService membership;

	@GetMapping("/projects")
	public List<ApiResponses.ProjectJson> projects() {
		return membership.readable(access.currentUsername(), reporting.listProjects())
			.stream()
			.map((p) -> ApiResponses.ProjectJson.of(p, reporting.runCount(p.getId())))
			.toList();
	}

	@GetMapping("/projects/{id}")
	public ResponseEntity<ApiResponses.ProjectJson> project(@PathVariable Long id) {
		Project p = access.requireReadProject(id);
		return ResponseEntity.ok(ApiResponses.ProjectJson.of(p, reporting.runCount(id)));
	}

	@GetMapping("/projects/{id}/flags")
	public ResponseEntity<List<FlagSummary>> flags(@PathVariable Long id) {
		access.requireReadProject(id);
		return ResponseEntity.ok(reporting.flagSummaries(id));
	}

	@GetMapping("/projects/{id}/pull-requests")
	public ResponseEntity<List<PullRequestSummary>> pullRequests(@PathVariable Long id) {
		access.requireReadProject(id);
		return ResponseEntity.ok(pullRequests.list(id));
	}

	@GetMapping("/projects/{id}/failure-clusters")
	public ResponseEntity<List<FailureCluster>> failureClusters(@PathVariable Long id) {
		access.requireReadProject(id);
		return ResponseEntity.ok(clustering.cluster(id));
	}

	@GetMapping("/projects/{id}/perf-trend")
	public ResponseEntity<List<org.alexmond.unitrack.report.PerfTrendPoint>> perfTrend(@PathVariable Long id,
			@RequestParam(defaultValue = "30") int limit) {
		access.requireReadProject(id);
		return ResponseEntity.ok(reporting.perfTrend(id, clamp(limit)));
	}

	@GetMapping("/projects/{id}/runs")
	public ResponseEntity<List<ApiResponses.RunJson>> runs(@PathVariable Long id,
			@RequestParam(defaultValue = "50") int limit) {
		access.requireReadProject(id);
		List<ApiResponses.RunJson> runs = reporting.recentRuns(id, clamp(limit))
			.stream()
			.map(ApiResponses.RunJson::of)
			.toList();
		return ResponseEntity.ok(runs);
	}

	@GetMapping("/runs/{id}")
	public ResponseEntity<ApiResponses.RunDetailJson> run(@PathVariable Long id) {
		TestRun run = access.requireReadRun(id);
		List<ApiResponses.SuiteJson> suites = reporting.suitesFor(id).stream().map(ApiResponses.SuiteJson::of).toList();
		List<ApiResponses.CaseJson> failures = reporting.failedCasesFor(id)
			.stream()
			.map(ApiResponses.CaseJson::of)
			.toList();
		ApiResponses.CoverageJson coverage = reporting.coverageFor(id).map(this::toCoverageJson).orElse(null);
		return ResponseEntity
			.ok(new ApiResponses.RunDetailJson(ApiResponses.RunJson.of(run), suites, failures, coverage));
	}

	private ApiResponses.CoverageJson toCoverageJson(CoverageReport report) {
		return ApiResponses.CoverageJson.of(report, reporting.coverageFiles(report.getId(), 500));
	}

	private static int clamp(int limit) {
		return Math.max(1, Math.min(limit, 500));
	}

}
