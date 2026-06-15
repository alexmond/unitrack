package org.alexmond.unitrack.web.mcp;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.ComparisonService;
import org.alexmond.unitrack.report.FailureClusteringService;
import org.alexmond.unitrack.report.FlakyTestService;
import org.alexmond.unitrack.report.QualityGateService;
import org.alexmond.unitrack.report.ReportingService;
import org.alexmond.unitrack.report.RunComparison;
import org.alexmond.unitrack.report.TestRegressionResult;
import org.alexmond.unitrack.report.TestRegressionService;
import org.alexmond.unitrack.web.account.MembershipService;
import org.alexmond.unitrack.web.account.ProjectAccessService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only MCP tools exposing UniTrack's data to AI assistants, backed by the existing
 * report services. Methods are annotated with {@link Tool} and registered as MCP tools by
 * {@link McpConfig}. Returns plain DTOs (never JPA entities) so the MCP JSON stays
 * stable.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UniTrackMcpTools {

	private static final int DEFAULT_RUN_LIMIT = 20;

	private final ReportingService reporting;

	private final QualityGateService qualityGate;

	private final FlakyTestService flaky;

	private final FailureClusteringService clustering;

	private final TestRegressionService regression;

	private final ComparisonService comparison;

	private final ProjectAccessService access;

	private final MembershipService membership;

	@Tool(description = "List all projects tracked by UniTrack, with their id, name, repo URL and total run count.")
	public List<ProjectInfo> listProjects() {
		return this.membership.readable(this.access.currentUsername(), this.reporting.listProjects())
			.stream()
			.map((p) -> new ProjectInfo(p.getId(), p.getName(), p.getRepoUrl(), this.reporting.runCount(p.getId())))
			.toList();
	}

	@Tool(description = "List the most recent test runs for a project (newest first): status, pass/fail totals, "
			+ "coverage and commit. Identify the project by numeric id or by name.")
	public List<RunSummary> getProjectRuns(@ToolParam(description = "Project numeric id or exact name") String project,
			@ToolParam(description = "Max runs to return (default 20)", required = false) Integer limit) {
		Project p = resolveProject(project);
		int max = (limit != null && limit > 0) ? limit : DEFAULT_RUN_LIMIT;
		return this.reporting.recentRuns(p.getId(), max).stream().map(UniTrackMcpTools::toSummary).toList();
	}

	@Tool(description = "Get full detail for one test run: summary metrics, the quality-gate verdict, coverage, "
			+ "and the list of failing test cases.")
	public RunDetail getRunDetail(@ToolParam(description = "Test run id") long runId) {
		TestRun run = readableRun(runId);
		List<FailingTest> failing = this.reporting.failedCasesFor(runId)
			.stream()
			.map(UniTrackMcpTools::toFailingTest)
			.toList();
		return new RunDetail(toSummary(run), gateInfo(runId), coverageInfo(runId), failing);
	}

	@Tool(description = "Get the quality-gate verdict for a run (passed/failed plus each rule's detail).")
	public GateInfo getQualityGate(@ToolParam(description = "Test run id") long runId) {
		readableRun(runId);
		GateInfo gate = gateInfo(runId);
		if (gate == null) {
			throw new IllegalArgumentException("No quality gate for run " + runId + " (run not found or no gate)");
		}
		return gate;
	}

	@Tool(description = "Get code-coverage for a run: line/branch percentages and covered/missed counts.")
	public CoverageInfo getCoverage(@ToolParam(description = "Test run id") long runId) {
		readableRun(runId);
		return coverageInfo(runId);
	}

	@Tool(description = "List flaky tests for a project (tests that both passed and failed for the same commit), "
			+ "with failure rate and status. Identify the project by numeric id or name.")
	public List<FlakyInfo> getFlakyTests(@ToolParam(description = "Project numeric id or exact name") String project) {
		Project p = resolveProject(project);
		return this.flaky.listFlaky(p.getId())
			.stream()
			.map((f) -> new FlakyInfo(f.className(), f.name(), f.failureRatePct(), f.flakyCommits(), f.status().name()))
			.toList();
	}

	@Tool(description = "List recent failure clusters for a project: failures grouped by a normalized signature, "
			+ "most frequent first. Identify the project by numeric id or name.")
	public List<ClusterInfo> getFailureClusters(
			@ToolParam(description = "Project numeric id or exact name") String project) {
		Project p = resolveProject(project);
		return this.clustering.cluster(p.getId())
			.stream()
			.map((c) -> new ClusterInfo(c.failureType(), c.sampleMessage(), c.occurrences(), c.distinctTests()))
			.toList();
	}

	@Tool(description = "Triage summary for a run: gate verdict, how many tests fail, which failures are NEW versus "
			+ "the baseline (the real regressions to look at first), and which failing tests are already known-flaky "
			+ "(likely noise, not a real break). Use this to explain a red build instead of reading raw stack traces.")
	public RunTriageSummary summarizeRun(@ToolParam(description = "Test run id") long runId) {
		TestRun run = readableRun(runId);
		List<TestCaseResult> failing = this.reporting.failedCasesFor(runId);
		TestRegressionResult reg = this.regression.diff(runId).orElse(null);
		// Known-flaky failing tests: failures that match an active flaky-tracked test.
		Set<String> flakyKeys = this.flaky.listFlaky(run.getProject().getId())
			.stream()
			.map((f) -> key(f.className(), f.name()))
			.collect(Collectors.toSet());
		List<FailingTest> flakyAmongFailures = failing.stream()
			.filter((t) -> flakyKeys.contains(key(t.getClassName(), t.getName())))
			.map(UniTrackMcpTools::toFailingTest)
			.toList();
		List<FailingTest> newFailures = (reg != null) ? reg.newFailures()
			.stream()
			.map((t) -> new FailingTest(t.className(), t.name(), "FAILED", t.failureType(), t.failureMessage()))
			.toList() : List.of();
		return new RunTriageSummary(toSummary(run), gateInfo(runId), failing.size(),
				(reg != null) && reg.baselineFound(), (reg != null) ? reg.baselineRunId() : null,
				(reg != null) ? reg.baseBranch() : null, newFailures.size(), newFailures, flakyAmongFailures);
	}

	@Tool(description = "Diff two runs (head versus base): which tests newly fail, which got fixed, which still fail, "
			+ "plus pass-rate, coverage and duration deltas. Useful to see what a change moved between two commits.")
	public RunDiff compareRuns(@ToolParam(description = "Base (earlier) run id") long baseRunId,
			@ToolParam(description = "Head (later) run id") long headRunId) {
		readableRun(baseRunId);
		readableRun(headRunId);
		RunComparison c = this.comparison.compare(baseRunId, headRunId)
			.orElseThrow(() -> new IllegalArgumentException("One or both runs not found"));
		return new RunDiff(baseRunId, headRunId, c.newlyFailing(), c.fixed(), c.stillFailing(), c.passRateDelta(),
				c.coverageDelta(), c.durationDeltaMs());
	}

	private static String key(String className, String name) {
		return ((className == null || className.isBlank()) ? "" : className + "#") + name;
	}

	private Project resolveProject(String project) {
		Project resolved;
		if (project != null && project.chars().allMatch(Character::isDigit) && !project.isBlank()) {
			resolved = this.reporting.findProject(Long.parseLong(project))
				.orElseThrow(() -> new IllegalArgumentException("No project with id " + project));
		}
		else {
			resolved = this.reporting.findProjectByName(project)
				.orElseThrow(() -> new IllegalArgumentException("No project named '" + project + "'"));
		}
		return this.access.requireRead(resolved);
	}

	/** Resolves a run (clear error if unknown) and enforces visibility on its project. */
	private TestRun readableRun(long runId) {
		TestRun run = this.reporting.findRun(runId)
			.orElseThrow(() -> new IllegalArgumentException("No run with id " + runId));
		this.access.requireRead(run.getProject());
		return run;
	}

	private GateInfo gateInfo(long runId) {
		return this.qualityGate.evaluate(runId)
			.map((g) -> new GateInfo(g.passed(), g.status(),
					g.rules().stream().map((r) -> new RuleInfo(r.name(), r.passed(), r.detail())).toList()))
			.orElse(null);
	}

	private CoverageInfo coverageInfo(long runId) {
		return this.reporting.coverageFor(runId)
			.map((c) -> new CoverageInfo(true, c.getLinePct(), c.getBranchPct(), c.getLineCovered(), c.getLineMissed(),
					c.getBranchCovered(), c.getBranchMissed()))
			.orElse(new CoverageInfo(false, 0, 0, 0, 0, 0, 0));
	}

	private static RunSummary toSummary(TestRun r) {
		return new RunSummary(r.getId(), r.getBranch(), r.getFlag(), r.getShortSha(), r.getStatus(), r.getTotalTests(),
				r.getPassed(), r.getFailed() + r.getErrors(), r.getSkipped(), r.passRate(), r.getLineCoveragePct(),
				r.getDurationMs(), String.valueOf(r.getCreatedAt()));
	}

	private static FailingTest toFailingTest(TestCaseResult t) {
		return new FailingTest(t.getClassName(), t.getName(), t.getStatus().name(), t.getFailureType(),
				t.getFailureMessage());
	}

	/** A tracked project. */
	public record ProjectInfo(Long id, String name, String repoUrl, long runCount) {
	}

	/** One test run's headline numbers. */
	public record RunSummary(Long runId, String branch, String flag, String commit, String status, int totalTests,
			int passed, int failed, int skipped, double passRatePct, Double lineCoveragePct, long durationMs,
			String createdAt) {
	}

	/** A run plus its gate verdict, coverage, and failing tests. */
	public record RunDetail(RunSummary summary, GateInfo gate, CoverageInfo coverage, List<FailingTest> failingTests) {
	}

	/** One failing/errored test case. */
	public record FailingTest(String className, String name, String status, String failureType, String failureMessage) {
	}

	/** Quality-gate verdict. */
	public record GateInfo(boolean passed, String status, List<RuleInfo> rules) {
	}

	/** One gate rule's outcome. */
	public record RuleInfo(String name, boolean passed, String detail) {
	}

	/**
	 * Coverage counters for a run; {@code present=false} when the run carried no
	 * coverage.
	 */
	public record CoverageInfo(boolean present, double linePct, double branchPct, int lineCovered, int lineMissed,
			int branchCovered, int branchMissed) {
	}

	/** A flaky test and how often it flips. */
	public record FlakyInfo(String className, String name, double failureRatePct, long flakyCommits, String status) {
	}

	/** A cluster of similar failures. */
	public record ClusterInfo(String failureType, String sampleMessage, int occurrences, int distinctTests) {
	}

	/**
	 * Actionable summary of a (usually red) run: the gate verdict, total failing count,
	 * the failures that are NEW versus the baseline (the real regressions), and the
	 * failing tests that are already known-flaky (likely noise).
	 */
	public record RunTriageSummary(RunSummary summary, GateInfo gate, int totalFailing, boolean baselineFound,
			Long baselineRunId, String baseBranch, int newFailureCount, List<FailingTest> newFailures,
			List<FailingTest> flakyAmongFailures) {
	}

	/**
	 * Diff between two runs ({@code head} minus {@code base}); test entries are
	 * {@code class#name} strings. {@code coverageDelta} is null when either run lacks
	 * coverage.
	 */
	public record RunDiff(long baseRunId, long headRunId, List<String> newlyFailing, List<String> fixed,
			List<String> stillFailing, double passRateDelta, Double coverageDelta, long durationDeltaMs) {
	}

}
