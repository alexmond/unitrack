package org.alexmond.unitrack.web.ui;

import org.alexmond.unitrack.domain.CoverageReport;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.BlameService;
import org.alexmond.unitrack.report.BranchService;
import org.alexmond.unitrack.report.BranchSummary;
import org.alexmond.unitrack.report.CoverageDiffService;
import org.alexmond.unitrack.report.DurationPoint;
import org.alexmond.unitrack.report.FailureClusteringService;
import org.alexmond.unitrack.report.TestTimelinePoint;
import org.alexmond.unitrack.report.PerfRegressionService;
import org.alexmond.unitrack.report.PerfRunDetailService;
import org.alexmond.unitrack.report.PerfTrendPoint;
import org.alexmond.unitrack.report.PerformanceService;
import org.alexmond.unitrack.report.PerformanceSummary;
import org.alexmond.unitrack.report.ProjectHealth;
import org.alexmond.unitrack.report.ProjectHealthService;
import org.alexmond.unitrack.report.ProjectSettingsService;
import org.alexmond.unitrack.report.PullRequestService;
import org.alexmond.unitrack.report.QualityGateService;
import org.alexmond.unitrack.report.ReportingService;
import org.alexmond.unitrack.report.TestRegressionService;
import org.alexmond.unitrack.report.TriageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Server-rendered dashboard (Thymeleaf). */
@Controller
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardController {

	private static final int RUN_LIST_LIMIT = 50;

	private static final int TREND_LIMIT = 30;

	private static final int COVERAGE_FILE_LIMIT = 200;

	private static final int SLOWEST_IN_RUN_LIMIT = 10;

	private static final int PERF_SLOW_LIMIT = 25;

	private final ReportingService reporting;

	private final QualityGateService qualityGate;

	private final FailureClusteringService clustering;

	private final TriageService triage;

	private final TestRegressionService regression;

	private final PerformanceService performance;

	private final BlameService blame;

	private final PerfRegressionService perfRegression;

	private final PerfRunDetailService perfRunDetail;

	private final CoverageDiffService coverageDiff;

	private final PullRequestService pullRequests;

	private final ProjectSettingsService settings;

	private final BranchService branchService;

	private final ProjectHealthService projectHealth;

	@GetMapping("/")
	public String index(Model model) {
		List<ProjectHealth> board = projectHealth.board();
		model.addAttribute("board", board);
		model.addAttribute("summary", ProjectHealthService.summarize(board));
		return "index";
	}

	@GetMapping("/projects/{id}")
	public String project(@PathVariable Long id, @RequestParam(required = false) String branch, Model model) {
		Project project = reporting.findProject(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
		List<BranchSummary> branchSummaries = branchService.list(id);
		List<String> branches = branchSummaries.stream().map(BranchSummary::branch).toList();
		String selectedBranch = resolveBranch(id, branch, branches);
		List<TestRun> runs = reporting.recentRuns(id, selectedBranch, RUN_LIST_LIMIT);
		List<TestRun> trend = reporting.trendRuns(id, selectedBranch, TREND_LIMIT);

		model.addAttribute("project", project);
		model.addAttribute("branches", branches);
		model.addAttribute("selectedBranch", selectedBranch);
		model.addAttribute("branchSummaries", branchSummaries);
		model.addAttribute("runs", runs);
		model.addAttribute("flags", reporting.flagSummaries(id));
		model.addAttribute("trendLabels", toJson(labels(trend.stream().map(TestRun::getShortSha).toList())));
		model.addAttribute("trendPassRate", toJson(trend.stream().map((r) -> round(r.passRate())).toList()));
		model.addAttribute("trendCoverage", toJson(trend.stream().map(TestRun::getLineCoveragePct).toList()));
		model.addAttribute("pullRequests", pullRequests.list(id));
		model.addAttribute("uploadSnippet", uploadSnippet(project.getName()));
		return "project";
	}

	@GetMapping("/projects/{id}/coverage")
	public String coverage(@PathVariable Long id, Model model) {
		Project project = reporting.findProject(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
		model.addAttribute("project", project);
		Optional<CoverageReport> report = reporting.latestCoverage(id);
		model.addAttribute("coverage", report.orElse(null));
		report.ifPresent((c) -> {
			model.addAttribute("run", c.getRun());
			model.addAttribute("packages", reporting.coveragePackages(c.getId()));
			model.addAttribute("worstFiles", reporting.coverageFiles(c.getId(), COVERAGE_FILE_LIMIT));
			List<TestRun> trend = reporting.trendRuns(id, TREND_LIMIT);
			model.addAttribute("trendLabels", toJson(labels(trend.stream().map(TestRun::getShortSha).toList())));
			model.addAttribute("trendCoverage", toJson(trend.stream().map(TestRun::getLineCoveragePct).toList()));
		});
		return "coverage";
	}

	@GetMapping("/projects/{id}/pr/{pr}")
	public String pullRequest(@PathVariable Long id, @PathVariable Integer pr, Model model) {
		Project project = reporting.findProject(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
		List<TestRun> prRuns = pullRequests.runsFor(id, pr);
		if (prRuns.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pull request not found");
		}
		TestRun latest = prRuns.get(0);
		model.addAttribute("project", project);
		model.addAttribute("pr", pr);
		model.addAttribute("latest", latest);
		model.addAttribute("runs", prRuns);
		model.addAttribute("gate", qualityGate.evaluate(latest.getId()).orElse(null));
		model.addAttribute("regression", regression.diff(latest.getId()).orElse(null));
		model.addAttribute("coverageDiff", coverageDiff.diff(latest.getId()).orElse(null));
		return "pr";
	}

	@GetMapping("/projects/{id}/clusters")
	public String clusters(@PathVariable Long id, Model model) {
		Project project = reporting.findProject(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
		model.addAttribute("project", project);
		model.addAttribute("clusters", clustering.cluster(id));
		return "clusters";
	}

	@GetMapping("/runs/{id}")
	public String run(@PathVariable Long id, Model model) {
		TestRun run = reporting.findRun(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
		model.addAttribute("run", run);
		model.addAttribute("gate", qualityGate.evaluate(id).orElse(null));
		model.addAttribute("regression", regression.diff(id).orElse(null));
		model.addAttribute("perfRegression", perfRegression.diff(id).orElse(null));
		model.addAttribute("suites", reporting.suitesFor(id));
		List<TestCaseResult> failures = reporting.failedCasesFor(id);
		model.addAttribute("failures", failures);
		model.addAttribute("categories", triage.categoryByCaseId(run.getProject().getId(), failures));
		model.addAttribute("blame", blame.blameByCaseId(run, failures));

		Optional<CoverageReport> coverage = reporting.coverageFor(id);
		model.addAttribute("coverage", coverage.orElse(null));
		List<?> files = coverage.map((c) -> reporting.coverageFiles(c.getId(), COVERAGE_FILE_LIMIT)).orElse(List.of());
		model.addAttribute("coverageFiles", files);
		model.addAttribute("coverageDiff", coverageDiff.diff(id).orElse(null));
		model.addAttribute("slowest", performance.slowestInRun(id, SLOWEST_IN_RUN_LIMIT));
		return "run";
	}

	@GetMapping("/projects/{id}/performance")
	public String performance(@PathVariable Long id, Model model) {
		Project project = reporting.findProject(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
		PerformanceSummary summary = performance.summary(id, PERF_SLOW_LIMIT, TREND_LIMIT);
		model.addAttribute("project", project);
		model.addAttribute("slowest", summary.slowestInLatestRun());
		model.addAttribute("latestRunId", summary.latestRunId());
		model.addAttribute("trendLabels",
				toJson(labels(summary.suiteTimeTrend().stream().map(DurationPoint::shortSha).toList())));
		model.addAttribute("trendSeconds",
				toJson(summary.suiteTimeTrend().stream().map((p) -> round(p.durationMs() / 1000.0)).toList()));
		return "performance";
	}

	@GetMapping("/projects/{id}/perf")
	public String perf(@PathVariable Long id, Model model) {
		Project project = reporting.findProject(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
		List<PerfTrendPoint> trend = reporting.perfTrend(id, TREND_LIMIT);
		model.addAttribute("project", project);
		model.addAttribute("perfRuns", reporting.recentPerfRuns(id, RUN_LIST_LIMIT));
		model.addAttribute("hasPerf", !trend.isEmpty());
		model.addAttribute("trendLabels", toJson(labels(trend.stream().map(PerfTrendPoint::shortSha).toList())));
		model.addAttribute("trendP50", toJson(trend.stream().map((p) -> round(p.p50Ms())).toList()));
		model.addAttribute("trendP90", toJson(trend.stream().map((p) -> round(p.p90Ms())).toList()));
		model.addAttribute("trendP99", toJson(trend.stream().map((p) -> round(p.p99Ms())).toList()));
		model.addAttribute("trendThroughput", toJson(trend.stream().map((p) -> round(p.throughputRps())).toList()));
		model.addAttribute("trendError", toJson(trend.stream().map((p) -> round(p.errorPct())).toList()));
		return "perf";
	}

	@GetMapping("/perf-runs/{id}")
	public String perfRun(@PathVariable Long id, Model model) {
		model.addAttribute("detail", perfRunDetail.detail(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perf run not found")));
		return "perf-run";
	}

	@GetMapping("/projects/{id}/test")
	public String test(@PathVariable Long id, @RequestParam(defaultValue = "") String className,
			@RequestParam String name, Model model) {
		Project project = reporting.findProject(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
		List<TestTimelinePoint> timeline = performance.testStatusTimeline(id, className, name, TREND_LIMIT);
		model.addAttribute("project", project);
		model.addAttribute("className", className);
		model.addAttribute("name", name);
		model.addAttribute("timeline", timeline);
		model.addAttribute("blame", blame.firstFailingForTest(id, className, name).orElse(null));
		model.addAttribute("trendLabels", toJson(labels(timeline.stream().map(TestTimelinePoint::shortSha).toList())));
		model.addAttribute("trendMs", toJson(timeline.stream().map(TestTimelinePoint::durationMs).toList()));
		return "test";
	}

	/**
	 * The branch the Overview is scoped to. An explicit {@code branch} param wins (an
	 * unknown or blank value means "all branches"); with no param we default to the gate
	 * base branch when it has runs, otherwise all branches so the page is never empty.
	 */
	private String resolveBranch(Long projectId, String branch, List<String> branches) {
		if (branch != null) {
			return branches.contains(branch) ? branch : null;
		}
		String base = settings.gateConfig(projectId).baseBranch();
		return branches.contains(base) ? base : null;
	}

	/**
	 * A ready-to-paste CLI command for pushing this project's results, using the current
	 * server URL.
	 */
	private static String uploadSnippet(String projectName) {
		String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
		return "java -jar unitrack-cli.jar upload \\\n" + "  --url " + base + " \\\n" + "  --project \"" + projectName
				+ "\" \\\n" + "  --junit \"target/surefire-reports/*.xml\" \\\n"
				+ "  --jacoco \"target/site/jacoco/jacoco.xml\"";
	}

	/** Trend X labels with duplicate SHAs disambiguated (a re-run of the same commit). */
	private static List<String> labels(List<String> shas) {
		Map<String, Integer> seen = new HashMap<>();
		List<String> out = new ArrayList<>(shas.size());
		for (String s : shas) {
			String label = (s == null || s.isBlank()) ? "—" : s;
			int n = seen.merge(label, 1, Integer::sum);
			out.add((n == 1) ? label : label + " ·" + n);
		}
		return out;
	}

	/** Serializes a list of Strings/Numbers/nulls to a JSON array for the trend chart. */
	private static String toJson(List<?> values) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				sb.append(',');
			}
			Object v = values.get(i);
			if (v == null) {
				sb.append("null");
			}
			else if (v instanceof Number) {
				sb.append(v);
			}
			else {
				sb.append('"').append(escape(v.toString())).append('"');
			}
		}
		return sb.append(']').toString();
	}

	private static String escape(String s) {
		StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (c < 0x20) {
						out.append(String.format("\\u%04x", (int) c));
					}
					else {
						out.append(c);
					}
				}
			}
		}
		return out.toString();
	}

	private static double round(double value) {
		return Math.round(value * 10.0) / 10.0;
	}

}
