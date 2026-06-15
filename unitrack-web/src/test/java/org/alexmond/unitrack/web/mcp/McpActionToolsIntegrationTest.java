package org.alexmond.unitrack.web.mcp;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

import org.alexmond.unitrack.domain.AuditEntry;
import org.alexmond.unitrack.domain.FlakyStatus;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.ingest.IngestRequest;
import org.alexmond.unitrack.ingest.IngestService;
import org.alexmond.unitrack.report.FlakyTestService;
import org.alexmond.unitrack.report.TriageService;
import org.alexmond.unitrack.web.account.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The gated MCP action tools, with writes enabled and an admin principal: each authorized
 * action performs the change and appends an audit entry, and the hallucination guards
 * reject targets that don't exist.
 */
@SpringBootTest
@TestPropertySource(properties = "unitrack.mcp.writes-enabled=true")
@WithMockUser(username = "admin", roles = "ADMIN")
class McpActionToolsIntegrationTest {

	@Autowired
	private UniTrackMcpActionTools actions;

	@Autowired
	private IngestService ingest;

	@Autowired
	private TriageService triage;

	@Autowired
	private FlakyTestService flaky;

	@Autowired
	private AuditService audit;

	private static List<Supplier<InputStream>> stream(String xml) {
		byte[] data = xml.getBytes(StandardCharsets.UTF_8);
		return List.of(() -> new ByteArrayInputStream(data));
	}

	private static String junit(boolean fail) {
		String tc = fail
				? "<testcase name=\"a\" classname=\"com.x.G\" time=\"0.1\"><failure message=\"boom\" "
						+ "type=\"java.lang.AssertionError\">trace</failure></testcase>"
				: "<testcase name=\"a\" classname=\"com.x.G\" time=\"0.1\"/>";
		return "<?xml version=\"1.0\"?><testsuite name=\"com.x.G\" tests=\"1\" failures=\"" + (fail ? 1 : 0)
				+ "\" errors=\"0\" skipped=\"0\" time=\"0.1\">" + tc + "</testsuite>";
	}

	private TestRun ingestRun(String project, String commit, boolean fail) {
		IngestRequest meta = new IngestRequest(project, "https://github.com/acme/" + project, "main", "default", commit,
				null, null, null);
		return this.ingest.ingest(meta, stream(junit(fail)), List.of());
	}

	@Test
	void createTriageRulePersistsAndAudits() {
		Long projectId = ingestRun("act-triage", "c1", true).getProject().getId();

		String msg = this.actions.createTriageRule("act-triage", "timeouts", "infra", "AssertionError", null);
		assertThat(msg).contains("timeouts");

		assertThat(this.triage.listRules(projectId)).anySatisfy((r) -> assertThat(r.category()).isEqualTo("infra"));
		assertThat(this.audit.recentForProject(projectId, 10))
			.anySatisfy((e) -> assertThat(e.getAction()).isEqualTo("CREATE_TRIAGE_RULE"));
	}

	@Test
	void quarantineFlakyTestSetsStatusAndAudits() {
		// Same commit passing then failing => 'a' is detected flaky.
		ingestRun("act-flaky", "flk1", false);
		Long projectId = ingestRun("act-flaky", "flk1", true).getProject().getId();

		String msg = this.actions.quarantineFlakyTest("act-flaky", "com.x.G", "a", "QUARANTINED", "muted by AI");
		assertThat(msg).contains("QUARANTINED");

		assertThat(this.flaky.listFlaky(projectId))
			.anySatisfy((f) -> assertThat(f.status()).isEqualTo(FlakyStatus.QUARANTINED));
		List<AuditEntry> log = this.audit.recentForProject(projectId, 10);
		assertThat(log).anySatisfy((e) -> assertThat(e.getAction()).isEqualTo("QUARANTINE_FLAKY"));
		assertThat(log).allSatisfy((e) -> assertThat(e.getSource()).isEqualTo("MCP"));
	}

	@Test
	void guardsRejectUnknownTargets() {
		ingestRun("act-guard", "c1", true);
		assertThatThrownBy(() -> this.actions.quarantineFlakyTest("act-guard", "com.x.Nope", "z", "QUARANTINED", null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("No flaky test");
		assertThatThrownBy(() -> this.actions.ackFailureCluster("act-guard", "does.not.Exist", "known-issue"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("No failure cluster");
	}

}
