package org.alexmond.unitrack.web.mcp;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.FailureClusteringService;
import org.alexmond.unitrack.report.FlakyTestService;
import org.alexmond.unitrack.report.QualityGateService;
import org.alexmond.unitrack.report.ReportingService;
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

	@Tool(description = "List all projects tracked by UniTrack, with their id, name, repo URL and total run count.")
	public List<ProjectInfo> listProjects() {
		return this.reporting.listProjects()
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
		TestRun run = this.reporting.findRun(runId)
			.orElseThrow(() -> new IllegalArgumentException("No run with id " + runId));
		List<FailingTest> failing = this.reporting.failedCasesFor(runId)
			.stream()
			.map(UniTrackMcpTools::toFailingTest)
			.toList();
		return new RunDetail(toSummary(run), gateInfo(runId), coverageInfo(runId), failing);
	}

	@Tool(description = "Get the quality-gate verdict for a run (passed/failed plus each rule's detail).")
	public GateInfo getQualityGate(@ToolParam(description = "Test run id") long runId) {
		GateInfo gate = gateInfo(runId);
		if (gate == null) {
			throw new IllegalArgumentException("No quality gate for run " + runId + " (run not found or no gate)");
		}
		return gate;
	}

	@Tool(description = "Get code-coverage for a run: line/branch percentages and covered/missed counts.")
	public CoverageInfo getCoverage(@ToolParam(description = "Test run id") long runId) {
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

	private Project resolveProject(String project) {
		if (project != null && project.chars().allMatch(Character::isDigit) && !project.isBlank()) {
			return this.reporting.findProject(Long.parseLong(project))
				.orElseThrow(() -> new IllegalArgumentException("No project with id " + project));
		}
		return this.reporting.findProjectByName(project)
			.orElseThrow(() -> new IllegalArgumentException("No project named '" + project + "'"));
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

}
