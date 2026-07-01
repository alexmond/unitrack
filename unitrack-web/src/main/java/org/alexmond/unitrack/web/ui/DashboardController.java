package org.alexmond.unitrack.web.ui;

import org.alexmond.unitrack.domain.CoverageReport;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.TestStatus;
import org.alexmond.unitrack.report.BlameService;
import org.alexmond.unitrack.report.BranchService;
import org.alexmond.unitrack.report.BranchSummary;
import org.alexmond.unitrack.report.CoverageDiffService;
import org.alexmond.unitrack.report.DurationPoint;
import org.alexmond.unitrack.report.FailureCluster;
import org.alexmond.unitrack.report.FailureClusteringService;
import org.alexmond.unitrack.report.FlakyTestService;
import org.alexmond.unitrack.report.FlakyTestView;
import org.alexmond.unitrack.report.TestTimelinePoint;
import org.alexmond.unitrack.report.PerfRegressionService;
import org.alexmond.unitrack.report.PerfRunDetailService;
import org.alexmond.unitrack.report.PerfStepDetectionService;
import org.alexmond.unitrack.report.PerfTrendPoint;
import org.alexmond.unitrack.report.PerformanceService;
import org.alexmond.unitrack.report.PerformanceSummary;
import org.alexmond.unitrack.report.OwnershipService;
import org.alexmond.unitrack.report.ProjectHealth;
import org.alexmond.unitrack.report.ProjectHealthService;
import org.alexmond.unitrack.report.ProjectSettingsService;
import org.alexmond.unitrack.report.PullRequestService;
import org.alexmond.unitrack.report.QualityGateService;
import org.alexmond.unitrack.report.ReportingService;
import org.alexmond.unitrack.report.TestRegressionService;
import org.alexmond.unitrack.report.TestModuleRow;
import org.alexmond.unitrack.report.TestRosterRow;
import org.alexmond.unitrack.report.TriageService;
import org.alexmond.unitrack.web.account.MembershipService;
import org.alexmond.unitrack.web.ai.AiAnalyzer;
import org.alexmond.unitrack.web.account.ProjectAccessService;
import org.alexmond.unitrack.web.account.ShareLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

	/**
	 * Trends chart one flag — the default/rollup series — so split-by-module flags don't
	 * interleave.
	 */
	private static final String TREND_FLAG = "default";

	private static final int COVERAGE_FILE_LIMIT = 200;

	private static final int SLOWEST_IN_RUN_LIMIT = 10;

	private static final int PERF_SLOW_LIMIT = 25;

	private final ReportingService reporting;

	private final QualityGateService qualityGate;

	private final FailureClusteringService clustering;

	private final FlakyTestService flaky;

	private final TriageService triage;

	private final TestRegressionService regression;

	private final PerformanceService performance;

	private final BlameService blame;

	private final PerfRegressionService perfRegression;

	private final PerfRunDetailService perfRunDetail;

	private final PerfStepDetectionService perfStepDetection;

	private final CoverageDiffService coverageDiff;

	private final PullRequestService pullRequests;

	private final ProjectSettingsService settings;

	private final BranchService branchService;

	private final ProjectHealthService projectHealth;

	private final OwnershipService ownership;

	private final ProjectAccessService access;

	private final MembershipService membership;

	private final ShareLinkService shareLinks;

	private final AiAnalyzer aiAnalyzer;

	private final io.micrometer.observation.ObservationRegistry observationRegistry;

	private final java.util.concurrent.ExecutorService pageRenderExecutor;

	/**
	 * Times one section of a page render as a child observation ({@code unitrack.page}
	 * with {@code page}/{@code section} tags) so the read-path breakdown is visible in
	 * metrics — the dashboard pages fan out across many services and we need to see which
	 * dominate (#280).
	 */
	private <T> T timed(String page, String section, java.util.function.Supplier<T> work) {
		return io.micrometer.observation.Observation.createNotStarted("unitrack.page", this.observationRegistry)
			.lowCardinalityKeyValue("page", page)
			.lowCardinalityKeyValue("section", section)
			.observe(work);
	}

	/**
	 * Runs a timed, self-contained read section on the page pool so independent sections
	 * execute concurrently (each service call opens its own read transaction). Only for
	 * sections returning detached DTOs that read no lazy associations off-thread (#280).
	 */
	private <T> java.util.concurrent.CompletableFuture<T> async(String page, String section,
			java.util.function.Supplier<T> work) {
		return java.util.concurrent.CompletableFuture.supplyAsync(() -> timed(page, section, work),
				this.pageRenderExecutor);
	}

	@GetMapping("/")
	public String index(Model model) {
		String user = access.currentUsername();
		List<ProjectHealth> board = projectHealth.board(membership.readableBy(user));
		model.addAttribute("board", board);
		model.addAttribute("summary", ProjectHealthService.summarize(board));
		return "index";
	}

	@GetMapping("/projects/{id}")
	public String project(@PathVariable Long id, @RequestParam(required = false) String branch, Model model) {
		Project project = access.requireReadProject(id);
		List<BranchSummary> branchSummaries = branchService.list(id);
		List<String> branches = branchSummaries.stream().map(BranchSummary::branch).toList();
		String selectedBranch = resolveBranch(id, branch, branches);
		List<TestRun> runs = reporting.recentRuns(id, selectedBranch, RUN_LIST_LIMIT);
		List<TestRun> trend = reporting.trendRuns(id, selectedBranch, TREND_FLAG, TREND_LIMIT);

		model.addAttribute("project", project);
		model.addAttribute("branches", branches);
		model.addAttribute("selectedBranch", selectedBranch);
		model.addAttribute("branchSummaries", branchSummaries);
		model.addAttribute("hiddenBranchCount",
				branchSummaries.stream().filter((b) -> !b.shown() && !b.branch().equals(selectedBranch)).count());
		model.addAttribute("runs", runs);
		model.addAttribute("flags", reporting.flagSummaries(id));
		model.addAttribute("modules",
				reporting.latestCoverage(id).map((c) -> reporting.moduleCoverage(c.getId())).orElse(List.of()));
		model.addAttribute("trendLabels", toJson(labels(trend.stream().map(TestRun::getShortSha).toList())));
		model.addAttribute("trendRunIds", toJson(trend.stream().map(TestRun::getId).toList()));
		model.addAttribute("trendTimes", toJson(trend.stream().map(DashboardController::epochMilli).toList()));
		model.addAttribute("trendPassed", toJson(trend.stream().map(TestRun::getPassed).toList()));
		model.addAttribute("trendFailed", toJson(trend.stream().map((r) -> r.getFailed() + r.getErrors()).toList()));
		model.addAttribute("trendSkipped", toJson(trend.stream().map(TestRun::getSkipped).toList()));
		model.addAttribute("trendCoverage", toJson(trend.stream().map(TestRun::getLineCoveragePct).toList()));
		model.addAttribute("pullRequests", pullRequests.list(id));
		model.addAttribute("uploadSnippet", uploadSnippet(project.getName()));
		return "project";
	}

	@GetMapping("/projects/{id}/coverage")
	public String coverage(@PathVariable Long id, @RequestParam(required = false) String module, Model model) {
		Project project = access.requireReadProject(id);
		String selectedModule = (module != null && !module.isBlank()) ? module : null;
		model.addAttribute("project", project);
		model.addAttribute("selectedModule", selectedModule);
		Optional<CoverageReport> report = reporting.latestCoverage(id);
		model.addAttribute("coverage", report.orElse(null));
		report.ifPresent((c) -> {
			model.addAttribute("run", c.getRun());
			model.addAttribute("modules", reporting.moduleCoverage(c.getId()));
			model.addAttribute("packages", reporting.coveragePackages(c.getId(), selectedModule));
			model.addAttribute("worstFiles", reporting.coverageFiles(c.getId(), selectedModule, COVERAGE_FILE_LIMIT));
			List<TestRun> trend = reporting.trendRuns(id, null, TREND_FLAG, TREND_LIMIT);
			model.addAttribute("trendLabels", toJson(labels(trend.stream().map(TestRun::getShortSha).toList())));
			model.addAttribute("trendRunIds", toJson(trend.stream().map(TestRun::getId).toList()));
			model.addAttribute("trendTimes", toJson(trend.stream().map(DashboardController::epochMilli).toList()));
			model.addAttribute("trendCoverage", toJson(trend.stream().map(TestRun::getLineCoveragePct).toList()));
			model.addAttribute("lineDelta", lineCoverageDelta(trend));
		});
		return "coverage";
	}

	@GetMapping("/projects/{id}/pr/{pr}")
	public String pullRequest(@PathVariable Long id, @PathVariable Integer pr, Model model) {
		Project project = access.requireReadProject(id);
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
		Project project = access.requireReadProject(id);
		model.addAttribute("project", project);
		// A real cluster spans >1 distinct test (shared root cause). A signature hit by a
		// single
		// test is just that test failing repeatedly — list those separately as recurring
		// failures.
		List<FailureCluster> all = clustering.cluster(id);
		model.addAttribute("clusters", all.stream().filter((c) -> c.distinctTests() > 1).toList());
		// AI root-cause is click-to-run (see analyzeCluster) — no LLM call on page load.
		model.addAttribute("aiEnabled", aiAnalyzer.enabled());
		// Recurring failures = a single test failing repeatedly with one signature.
		// Exclude tests
		// already flagged flaky (nondeterministic — pass+fail on a commit); those belong
		// in the Flaky
		// tab. What's left here is consistently-failing tests (standing bugs), with no
		// double-listing.
		java.util.Set<String> flakyKeys = flaky.listFlaky(id)
			.stream()
			.map(FlakyTestView::displayName)
			.collect(java.util.stream.Collectors.toSet());
		model.addAttribute("recurringFailures",
				all.stream().filter((c) -> c.distinctTests() == 1 && !flakyKeys.contains(c.tests().get(0))).toList());
		return "clusters";
	}

	/**
	 * Click-to-run AI root-cause for one cluster (htmx target on the clusters page).
	 * Returns just the analysis card fragment. Requires a logged-in user — LLM calls cost
	 * money, so anonymous visitors can't trigger them.
	 */
	@PostMapping("/projects/{id}/clusters/analyze")
	public String analyzeCluster(@PathVariable Long id, @RequestParam int index, Model model) {
		if (access.currentUsername() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to run AI analysis");
		}
		Project project = access.requireReadProject(id);
		List<FailureCluster> realClusters = clustering.cluster(id)
			.stream()
			.filter((c) -> c.distinctTests() > 1)
			.toList();
		if (index < 0 || index >= realClusters.size()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		model.addAttribute("analysis",
				aiAnalyzer.analyzeFailure(project.getName(), realClusters.get(index)).orElse(null));
		return "fragments/ai :: card";
	}

	@GetMapping("/runs/{id}")
	public String run(@PathVariable Long id, Model model) {
		TestRun run = access.requireReadRun(id);
		Long projectId = run.getProject().getId();
		model.addAttribute("run", run);
		model.addAttribute("prevRunId", reporting.previousRunId(run));
		model.addAttribute("nextRunId", reporting.nextRunId(run));

		// Independent, DTO-returning sections run concurrently (each opens its own read
		// tx).
		var gateF = async("run", "gate", () -> qualityGate.evaluate(id).orElse(null));
		var regressionF = async("run", "regression", () -> regression.diff(id).orElse(null));
		var perfRegressionF = async("run", "perfRegression", () -> perfRegression.diff(id).orElse(null));
		var coverageDiffF = async("run", "coverageDiff", () -> coverageDiff.diff(id).orElse(null));
		var slowestF = async("run", "slowest", () -> performance.slowestInRun(id, SLOWEST_IN_RUN_LIMIT));

		// failedCases is rendered and feeds triage/owners/blame; fetch it here, then
		// those three
		// (also DTO-returning, reading only the already-loaded scalar fields) run
		// concurrently.
		List<TestCaseResult> failures = timed("run", "failedCases", () -> reporting.failedCasesFor(id));
		var triageF = async("run", "triage", () -> triage.categoryByCaseId(projectId, failures));
		var ownersF = async("run", "owners", () -> ownership.ownerByCaseId(projectId, failures));
		var blameF = async("run", "blame", () -> blame.blameByCaseId(run, failures));

		// Entity-returning sections stay on the request thread (rendered under
		// open-in-view).
		model.addAttribute("suites", timed("run", "suites", () -> reporting.suitesFor(id)));
		Optional<CoverageReport> coverage = timed("run", "coverage", () -> reporting.coverageFor(id));
		model.addAttribute("coverage", coverage.orElse(null));
		model.addAttribute("coverageFiles", timed("run", "coverageFiles",
				() -> coverage.map((c) -> reporting.coverageFiles(c.getId(), COVERAGE_FILE_LIMIT)).orElse(List.of())));
		model.addAttribute("modules", timed("run", "modules",
				() -> coverage.map((c) -> reporting.moduleCoverage(c.getId())).orElse(List.of())));
		String username = access.currentUsername();
		boolean canShare = username != null && membership.canWrite(username, projectId);
		model.addAttribute("canShare", canShare);
		model.addAttribute("canDelete", canShare);
		model.addAttribute("shareLinks",
				timed("run", "shareLinks", () -> canShare ? shareLinks.listForRun(id) : List.of()));

		// Join the concurrent sections.
		model.addAttribute("failures", failures);
		model.addAttribute("gate", gateF.join());
		model.addAttribute("regression", regressionF.join());
		model.addAttribute("perfRegression", perfRegressionF.join());
		model.addAttribute("coverageDiff", coverageDiffF.join());
		model.addAttribute("slowest", slowestF.join());
		model.addAttribute("categories", triageF.join());
		model.addAttribute("owners", ownersF.join());
		model.addAttribute("blame", blameF.join());
		return "run";
	}

	@GetMapping("/projects/{id}/performance")
	public String performance(@PathVariable Long id, Model model) {
		Project project = access.requireReadProject(id);
		PerformanceSummary summary = performance.summary(id, TREND_FLAG, PERF_SLOW_LIMIT, TREND_LIMIT);
		List<DurationPoint> suiteTrend = summary.suiteTimeTrend();
		DurationPoint curPt = suiteTrend.isEmpty() ? null : suiteTrend.get(suiteTrend.size() - 1);
		DurationPoint prevPt = (suiteTrend.size() > 1) ? suiteTrend.get(suiteTrend.size() - 2) : null;
		model.addAttribute("project", project);
		model.addAttribute("slowest", summary.slowestInLatestRun());
		model.addAttribute("latestRunId", summary.latestRunId());
		model.addAttribute("timingModules",
				(summary.latestRunId() != null) ? reporting.testModuleTiming(summary.latestRunId()) : List.of());
		model.addAttribute("curSuiteMs", (curPt != null) ? curPt.durationMs() : null);
		model.addAttribute("suiteDeltaMs",
				(curPt != null && prevPt != null) ? curPt.durationMs() - prevPt.durationMs() : null);
		model.addAttribute("runsTracked", suiteTrend.size());
		model.addAttribute("trendLabels", toJson(labels(suiteTrend.stream().map(DurationPoint::shortSha).toList())));
		model.addAttribute("trendSeconds",
				toJson(suiteTrend.stream().map((p) -> round(p.durationMs() / 1000.0)).toList()));
		model.addAttribute("trendRunIds", toJson(suiteTrend.stream().map(DurationPoint::runId).toList()));
		model.addAttribute("trendTimes", toJson(suiteTrend.stream().map((p) -> p.createdAt().toEpochMilli()).toList()));
		return "performance";
	}

	@GetMapping("/projects/{id}/perf")
	public String perf(@PathVariable Long id, @RequestParam(required = false) String flag, Model model) {
		Project project = access.requireReadProject(id);
		List<String> perfFlags = reporting.perfFlags(id);
		String selectedFlag = selectedPerfFlag(flag, perfFlags);
		List<PerfTrendPoint> trend = reporting.perfTrend(id, selectedFlag, TREND_LIMIT);
		model.addAttribute("project", project);
		model.addAttribute("perfFlags", perfFlags);
		model.addAttribute("selectedFlag", selectedFlag);
		model.addAttribute("perfRuns", reporting.recentPerfRuns(id, selectedFlag, RUN_LIST_LIMIT));
		model.addAttribute("perfStep", perfStepDetection.detectLatencyStep(id, selectedFlag).orElse(null));
		model.addAttribute("hasPerf", !trend.isEmpty());
		model.addAttribute("trendLabels", toJson(labels(trend.stream().map(PerfTrendPoint::shortSha).toList())));
		model.addAttribute("trendP50", toJson(trend.stream().map((p) -> round(p.p50Ms())).toList()));
		model.addAttribute("trendP90", toJson(trend.stream().map((p) -> round(p.p90Ms())).toList()));
		model.addAttribute("trendP99", toJson(trend.stream().map((p) -> round(p.p99Ms())).toList()));
		model.addAttribute("trendThroughput", toJson(trend.stream().map((p) -> round(p.throughputRps())).toList()));
		model.addAttribute("trendError", toJson(trend.stream().map((p) -> round(p.errorPct())).toList()));
		model.addAttribute("trendRunIds", toJson(trend.stream().map(PerfTrendPoint::runId).toList()));
		model.addAttribute("trendTimes", toJson(trend.stream().map((p) -> p.createdAt().toEpochMilli()).toList()));
		return "perf";
	}

	/**
	 * The perf series to show: the requested flag if valid, else the {@code default}
	 * rollup, else the first.
	 */
	private static String selectedPerfFlag(String requested, List<String> perfFlags) {
		if (requested != null && !requested.isBlank() && perfFlags.contains(requested)) {
			return requested;
		}
		if (perfFlags.contains(TREND_FLAG)) {
			return TREND_FLAG;
		}
		return perfFlags.isEmpty() ? null : perfFlags.get(0);
	}

	@GetMapping("/perf-runs/{id}")
	public String perfRun(@PathVariable Long id, Model model) {
		org.alexmond.unitrack.domain.PerfRun run = access.requireReadPerfRun(id);
		model.addAttribute("detail", perfRunDetail.detail(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perf run not found")));
		model.addAttribute("prevRunId", reporting.previousPerfRunId(run));
		model.addAttribute("nextRunId", reporting.nextPerfRunId(run));
		return "perf-run";
	}

	@GetMapping("/projects/{id}/tests")
	public String tests(@PathVariable Long id, @RequestParam(required = false) String flag, Model model) {
		Project project = access.requireReadProject(id);
		List<String> flags = reporting.testFlags(id);
		String selectedFlag = pickFlag(flag, flags);

		List<TestRun> trend = reporting.trendRuns(id, null, selectedFlag, TREND_LIMIT);
		TestRun cur = trend.isEmpty() ? null : trend.get(trend.size() - 1);
		TestRun prev = (trend.size() > 1) ? trend.get(trend.size() - 2) : null;

		java.util.Set<String> flakyKeys = flaky.listFlaky(id)
			.stream()
			.map((v) -> testKey(v.className(), v.name()))
			.collect(java.util.stream.Collectors.toSet());
		List<TestRosterRow> roster = (cur != null) ? reporting.allCasesFor(cur.getId())
			.stream()
			.map((c) -> new TestRosterRow(c.getClassName(), c.getName(), c.getStatus().name(), c.getDurationMs(),
					flakyKeys.contains(testKey(c.getClassName(), c.getName())), false))
			.toList() : List.of();

		model.addAttribute("project", project);
		model.addAttribute("flags", flags);
		model.addAttribute("selectedFlag", selectedFlag);
		model.addAttribute("cur", cur);
		model.addAttribute("prev", prev);
		model.addAttribute("roster", roster);
		model.addAttribute("testModules", (cur != null) ? reporting.testModules(cur.getId()) : List.of());
		model.addAttribute("hasTrend", !trend.isEmpty());
		model.addAttribute("trendLabels", toJson(labels(trend.stream().map(TestRun::getShortSha).toList())));
		model.addAttribute("trendRunIds", toJson(trend.stream().map(TestRun::getId).toList()));
		model.addAttribute("trendTimes", toJson(trend.stream().map(DashboardController::epochMilli).toList()));
		model.addAttribute("trendPassed", toJson(trend.stream().map(TestRun::getPassed).toList()));
		model.addAttribute("trendFailed", toJson(trend.stream().map((r) -> r.getFailed() + r.getErrors()).toList()));
		return "tests";
	}

	/**
	 * Internal preview of the reconciled Tests page (P0, epic #390): the Tests aspect
	 * with Flaky and Failure-clusters folded in as sections, on the canonical analytics
	 * skeleton. Login-gated in {@code SecurityConfig} so it stays an internal test-drive
	 * surface; the shipped {@code /tests}, {@code /flaky}, and {@code /clusters} pages
	 * are untouched.
	 */
	@GetMapping("/projects/{id}/new-tests")
	public String newTests(@PathVariable Long id, @RequestParam(required = false) String flag,
			@RequestParam(required = false) String module, Model model) {
		if (access.currentUsername() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to preview the new Tests page");
		}
		Project project = access.requireReadProject(id);
		List<String> flags = reporting.testFlags(id);
		String selectedFlag = pickFlag(flag, flags);

		List<TestRun> trend = reporting.trendRuns(id, null, selectedFlag, TREND_LIMIT);
		TestRun cur = trend.isEmpty() ? null : trend.get(trend.size() - 1);
		TestRun prev = (trend.size() > 1) ? trend.get(trend.size() - 2) : null;

		List<FlakyTestView> flakyViews = flaky.listFlaky(id);
		java.util.Set<String> flakyKeys = flakyViews.stream()
			.map((v) -> testKey(v.className(), v.name()))
			.collect(java.util.stream.Collectors.toSet());
		// Tests that failed/errored in the previous run — a current PASS for one of these
		// is a
		// "fixed" (red→green) row, surfaced in the status column.
		java.util.Set<String> prevFailingKeys = (prev != null) ? reporting.allCasesFor(prev.getId())
			.stream()
			.filter((c) -> c.getStatus() == TestStatus.FAILED || c.getStatus() == TestStatus.ERROR)
			.map((c) -> testKey(c.getClassName(), c.getName()))
			.collect(java.util.stream.Collectors.toSet()) : java.util.Set.of();

		// By-module breakdown (empty for single-module projects). Clicking a row scopes
		// the
		// whole page to that module via ?module=; an unknown value falls back to "all".
		List<TestModuleRow> modules = (cur != null) ? reporting.testModules(cur.getId()) : List.of();
		java.util.Set<String> moduleNames = modules.stream()
			.map(TestModuleRow::name)
			.collect(java.util.stream.Collectors.toSet());
		String selectedModule = (module != null && moduleNames.contains(module)) ? module : null;
		boolean scoped = selectedModule != null;

		// The latest run's cases + each one's module label, so both the roster and the
		// KPI
		// tiles can be filtered to the selected module in one place.
		List<TestCaseResult> curCases = (cur != null) ? reporting.allCasesFor(cur.getId()) : List.of();
		List<String> curMods = reporting.moduleOf(curCases);
		List<TestRosterRow> roster = new ArrayList<>();
		for (int i = 0; i < curCases.size(); i++) {
			if (scoped && !selectedModule.equals(curMods.get(i))) {
				continue;
			}
			TestCaseResult c = curCases.get(i);
			String key = testKey(c.getClassName(), c.getName());
			boolean fixed = c.getStatus() == TestStatus.PASSED && prevFailingKeys.contains(key);
			roster.add(new TestRosterRow(c.getClassName(), c.getName(), c.getStatus().name(), c.getDurationMs(),
					flakyKeys.contains(key), fixed));
		}

		// KPI tiles: the run's stored aggregates when unscoped, computed from the
		// module's
		// cases (incl. the prev-run deltas) when scoped to one module.
		addTileStats(model, scoped, cur, prev, curCases, curMods, selectedModule);

		model.addAttribute("project", project);
		model.addAttribute("flags", flags);
		model.addAttribute("selectedFlag", selectedFlag);
		model.addAttribute("cur", cur);
		model.addAttribute("prev", prev);
		model.addAttribute("roster", roster);
		model.addAttribute("rosterFlaky", roster.stream().filter(TestRosterRow::flaky).count());
		model.addAttribute("rosterFixed", roster.stream().filter(TestRosterRow::fixed).count());
		model.addAttribute("testModules", modules);
		model.addAttribute("selectedModule", selectedModule);
		model.addAttribute("scoped", scoped);
		model.addAttribute("flakyList", flakyViews);
		model.addAttribute("aiEnabled", aiAnalyzer.enabled());
		addFoldedClusterAttrs(model, id, flakyViews);
		addTrendAttrs(model, trend, scoped, selectedModule);
		return "new-tests";
	}

	/**
	 * Folded Failure-clusters section attributes: a real cluster spans &gt;1 distinct
	 * test; a single test failing repeatedly is a recurring failure (minus ones already
	 * flagged flaky, which live in the Flaky section).
	 */
	private void addFoldedClusterAttrs(Model model, Long id, List<FlakyTestView> flakyViews) {
		List<FailureCluster> allClusters = clustering.cluster(id);
		java.util.Set<String> flakyDisplay = flakyViews.stream()
			.map(FlakyTestView::displayName)
			.collect(java.util.stream.Collectors.toSet());
		model.addAttribute("clusters", allClusters.stream().filter((c) -> c.distinctTests() > 1).toList());
		model.addAttribute("recurringFailures",
				allClusters.stream()
					.filter((c) -> c.distinctTests() == 1 && !flakyDisplay.contains(c.tests().get(0)))
					.toList());
	}

	/**
	 * Pass/fail trend model attributes — scoped to the selected module when one is
	 * picked, so the graph follows the breakdown click ("bring me to the group, graph
	 * included").
	 */
	private void addTrendAttrs(Model model, List<TestRun> trend, boolean scoped, String selectedModule) {
		model.addAttribute("hasTrend", !trend.isEmpty());
		model.addAttribute("trendLabels", toJson(labels(trend.stream().map(TestRun::getShortSha).toList())));
		model.addAttribute("trendRunIds", toJson(trend.stream().map(TestRun::getId).toList()));
		model.addAttribute("trendTimes", toJson(trend.stream().map(DashboardController::epochMilli).toList()));
		if (scoped) {
			List<int[]> mt = reporting.testModuleTrend(trend.stream().map(TestRun::getId).toList(), selectedModule);
			model.addAttribute("trendPassed", toJson(mt.stream().map((a) -> a[0]).toList()));
			model.addAttribute("trendFailed", toJson(mt.stream().map((a) -> a[1]).toList()));
		}
		else {
			model.addAttribute("trendPassed", toJson(trend.stream().map(TestRun::getPassed).toList()));
			model.addAttribute("trendFailed",
					toJson(trend.stream().map((r) -> r.getFailed() + r.getErrors()).toList()));
		}
	}

	/**
	 * Populate the KPI-tile model attributes ({@code statTests/statFailures/statSkipped/
	 * statDurationMs/statPassRate} + the {@code prev*} deltas). Unscoped reads the run's
	 * stored aggregates (authoritative); module-scoped sums the module's cases for both
	 * the current and previous run so the tiles and their deltas track the selected
	 * module.
	 */
	private void addTileStats(Model model, boolean scoped, TestRun cur, TestRun prev, List<TestCaseResult> curCases,
			List<String> curMods, String selectedModule) {
		if (cur == null) {
			return;
		}
		if (scoped) {
			long[] s = scopeStat(curCases, curMods, selectedModule);
			model.addAttribute("statTests", s[0]);
			model.addAttribute("statFailures", s[2]);
			model.addAttribute("statSkipped", s[3]);
			model.addAttribute("statDurationMs", s[4]);
			model.addAttribute("statPassRate", scopePassRate(s));
			if (prev != null) {
				List<TestCaseResult> pc = reporting.allCasesFor(prev.getId());
				long[] p = scopeStat(pc, reporting.moduleOf(pc), selectedModule);
				model.addAttribute("hasPrev", p[0] > 0);
				model.addAttribute("prevTests", p[0]);
				model.addAttribute("prevFailures", p[2]);
				model.addAttribute("prevDurationMs", p[4]);
				model.addAttribute("prevPassRate", scopePassRate(p));
			}
			else {
				model.addAttribute("hasPrev", false);
			}
			return;
		}
		model.addAttribute("statTests", (long) cur.getTotalTests());
		model.addAttribute("statFailures", (long) (cur.getFailed() + cur.getErrors()));
		model.addAttribute("statSkipped", (long) cur.getSkipped());
		model.addAttribute("statDurationMs", cur.getDurationMs());
		model.addAttribute("statPassRate", cur.passRate());
		model.addAttribute("hasPrev", prev != null);
		if (prev != null) {
			model.addAttribute("prevTests", (long) prev.getTotalTests());
			model.addAttribute("prevFailures", (long) (prev.getFailed() + prev.getErrors()));
			model.addAttribute("prevDurationMs", prev.getDurationMs());
			model.addAttribute("prevPassRate", prev.passRate());
		}
	}

	/**
	 * Sum {@code [tests, passed, failed+errors, skipped, durationMs]} over a module's
	 * cases (null module = all).
	 */
	private static long[] scopeStat(List<TestCaseResult> cases, List<String> mods, String module) {
		long tests = 0;
		long passed = 0;
		long failed = 0;
		long skipped = 0;
		long durMs = 0;
		for (int i = 0; i < cases.size(); i++) {
			if (module != null && !module.equals(mods.get(i))) {
				continue;
			}
			TestCaseResult c = cases.get(i);
			tests++;
			durMs += c.getDurationMs();
			TestStatus st = c.getStatus();
			if (st == TestStatus.PASSED) {
				passed++;
			}
			else if (st == TestStatus.SKIPPED) {
				skipped++;
			}
			else {
				failed++;
			}
		}
		return new long[] { tests, passed, failed, skipped, durMs };
	}

	/**
	 * Pass rate (skipped excluded from the denominator, matching
	 * {@link TestRun#passRate()}).
	 */
	private static double scopePassRate(long[] stat) {
		long considered = stat[0] - stat[3];
		return (considered <= 0) ? 100.0 : stat[1] * 100.0 / considered;
	}

	private static String testKey(String className, String name) {
		return ((className != null) ? className : "") + "#" + name;
	}

	/**
	 * Change in line coverage between the latest run carrying coverage and the previous
	 * one, or null.
	 */
	private static Double lineCoverageDelta(List<TestRun> trendOldestFirst) {
		Double cur = null;
		Double prev = null;
		for (TestRun r : trendOldestFirst) {
			if (r.getLineCoveragePct() != null) {
				prev = cur;
				cur = r.getLineCoveragePct();
			}
		}
		return (cur != null && prev != null) ? cur - prev : null;
	}

	/**
	 * The flag to show: the requested one if valid, else the {@code default} rollup, else
	 * the first.
	 */
	private String pickFlag(String requested, List<String> flags) {
		if (requested != null && !requested.isBlank() && flags.contains(requested)) {
			return requested;
		}
		if (flags.contains(TREND_FLAG)) {
			return TREND_FLAG;
		}
		return flags.isEmpty() ? TREND_FLAG : flags.get(0);
	}

	@GetMapping("/projects/{id}/test")
	public String test(@PathVariable Long id, @RequestParam(defaultValue = "") String className,
			@RequestParam String name, Model model) {
		Project project = access.requireReadProject(id);
		List<TestTimelinePoint> timeline = performance.testStatusTimeline(id, className, name, TREND_FLAG, TREND_LIMIT);
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

	/** Run timestamp as epoch millis for the time-based trend x-axis (null-safe). */
	private static Long epochMilli(TestRun run) {
		return (run.getCreatedAt() != null) ? run.getCreatedAt().toEpochMilli() : null;
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
