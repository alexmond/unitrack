package org.alexmond.unitrack.web.mcp;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.ingest.IngestRequest;
import org.alexmond.unitrack.ingest.IngestService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the read-only MCP tools end to end and confirms they register with the MCP
 * server.
 */
@SpringBootTest
class McpToolsIntegrationTest {

	@Autowired
	private UniTrackMcpTools tools;

	@Autowired
	private IngestService ingest;

	@Autowired
	private ToolCallbackProvider toolCallbackProvider;

	private static List<Supplier<InputStream>> stream(String xml) {
		byte[] data = xml.getBytes(StandardCharsets.UTF_8);
		return List.of(() -> new ByteArrayInputStream(data));
	}

	private static String junit(boolean fail) {
		String tc = fail
				? "<testcase name=\"a\" classname=\"com.x.G\" time=\"0.3\"><failure message=\"boom\" "
						+ "type=\"java.lang.AssertionError\">trace</failure></testcase>"
				: "<testcase name=\"a\" classname=\"com.x.G\" time=\"0.3\"/>";
		return "<?xml version=\"1.0\"?><testsuite name=\"com.x.G\" tests=\"1\" failures=\"" + (fail ? 1 : 0)
				+ "\" errors=\"0\" skipped=\"0\" time=\"0.3\">" + tc + "</testsuite>";
	}

	private static final String JACOCO = "<?xml version=\"1.0\"?><report name=\"r\">"
			+ "<counter type=\"LINE\" missed=\"3\" covered=\"7\"/>"
			+ "<package name=\"p\"><sourcefile name=\"F.java\"><counter type=\"LINE\" missed=\"3\" covered=\"7\"/>"
			+ "</sourcefile></package></report>";

	private TestRun ingestRun(String project, String commit, boolean fail, boolean withCoverage) {
		IngestRequest meta = new IngestRequest(project, "https://github.com/acme/" + project, "main", "default", commit,
				null, null, null);
		return this.ingest.ingest(meta, stream(junit(fail)), withCoverage ? stream(JACOCO) : List.of());
	}

	@Test
	void toolsExposeProjectsRunsGateCoverageAndFailures() {
		TestRun run = ingestRun("mcp-demo", "abc1234", true, true);
		Long projectId = run.getProject().getId();

		// listProjects
		assertThat(this.tools.listProjects()).anySatisfy((p) -> {
			assertThat(p.name()).isEqualTo("mcp-demo");
			assertThat(p.runCount()).isGreaterThanOrEqualTo(1);
		});

		// getProjectRuns by name and by id
		assertThat(this.tools.getProjectRuns("mcp-demo", null)).isNotEmpty();
		assertThat(this.tools.getProjectRuns(String.valueOf(projectId), 5)).isNotEmpty();

		// getRunDetail: failed run with a failing case, a gate verdict and coverage
		UniTrackMcpTools.RunDetail detail = this.tools.getRunDetail(run.getId());
		assertThat(detail.summary().status()).isEqualTo("FAILED");
		assertThat(detail.failingTests()).anySatisfy((t) -> assertThat(t.name()).isEqualTo("a"));
		assertThat(detail.gate()).isNotNull();
		assertThat(detail.coverage().present()).isTrue();

		// getQualityGate + getCoverage
		assertThat(this.tools.getQualityGate(run.getId()).status()).isIn("PASSED", "FAILED");
		assertThat(this.tools.getCoverage(run.getId()).linePct()).isGreaterThan(0.0);

		// flaky + clusters are non-null (clusters present since the run has a failure)
		assertThat(this.tools.getFlakyTests("mcp-demo")).isNotNull();
		assertThat(this.tools.getFailureClusters(String.valueOf(projectId))).isNotNull();
	}

	@Test
	void summarizeAndCompareSurfaceRegressionsBetweenRuns() {
		TestRun green = ingestRun("mcp-triage", "green01", false, false);
		TestRun red = ingestRun("mcp-triage", "red0001", true, false);

		// summarizeRun: the red run regresses against the green baseline.
		UniTrackMcpTools.RunTriageSummary summary = this.tools.summarizeRun(red.getId());
		assertThat(summary.summary().status()).isEqualTo("FAILED");
		assertThat(summary.totalFailing()).isGreaterThanOrEqualTo(1);
		assertThat(summary.baselineFound()).isTrue();
		assertThat(summary.baselineRunId()).isEqualTo(green.getId());
		assertThat(summary.newFailures()).anySatisfy((t) -> assertThat(t.name()).isEqualTo("a"));

		// compareRuns: 'a' newly fails moving green -> red.
		UniTrackMcpTools.RunDiff diff = this.tools.compareRuns(green.getId(), red.getId());
		assertThat(diff.newlyFailing()).contains("com.x.G#a");
		assertThat(diff.fixed()).isEmpty();
	}

	@Test
	void coverageReportsAbsentWhenRunHasNoCoverage() {
		TestRun run = ingestRun("mcp-nocov", "def5678", false, false);
		assertThat(this.tools.getCoverage(run.getId()).present()).isFalse();
	}

	@Test
	void unknownProjectAndRunRaiseClearErrors() {
		assertThatThrownBy(() -> this.tools.getFlakyTests("does-not-exist"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("No project");
		assertThatThrownBy(() -> this.tools.getRunDetail(999999L)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("No run");
	}

	@Test
	void mcpServerRegistersAllReadOnlyTools() {
		ToolCallback[] callbacks = this.toolCallbackProvider.getToolCallbacks();
		List<String> names = List.of(callbacks).stream().map((c) -> c.getToolDefinition().name()).toList();
		assertThat(names).contains("listProjects", "getProjectRuns", "getRunDetail", "getQualityGate", "getCoverage",
				"getFlakyTests", "getFailureClusters", "summarizeRun", "compareRuns");
	}

}
