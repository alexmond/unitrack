package org.alexmond.unitrack.web.mcp;

import org.alexmond.unitrack.report.FailureClusteringService;
import org.alexmond.unitrack.report.FlakyTestService;
import org.alexmond.unitrack.report.ReportingService;
import org.alexmond.unitrack.report.TriageService;
import org.alexmond.unitrack.web.account.AuditService;
import org.alexmond.unitrack.web.account.ProjectAccessService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * With {@code unitrack.mcp.writes-enabled=false} (the default), every action tool refuses
 * before touching any data — the disabled check is the very first thing each tool does.
 */
class McpActionToolsDisabledTest {

	private final ReportingService reporting = mock(ReportingService.class);

	private final FlakyTestService flaky = mock(FlakyTestService.class);

	private final TriageService triage = mock(TriageService.class);

	private final FailureClusteringService clustering = mock(FailureClusteringService.class);

	private final ProjectAccessService access = mock(ProjectAccessService.class);

	private final AuditService audit = mock(AuditService.class);

	private final UniTrackMcpActionTools tools = new UniTrackMcpActionTools(reporting, flaky, triage, clustering,
			access, audit, new McpProperties());

	@Test
	void allWriteToolsRefuseWhenDisabledAndTouchNothing() {
		assertThatThrownBy(() -> tools.quarantineFlakyTest("p", "C", "m", "QUARANTINED", null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("disabled");
		assertThatThrownBy(() -> tools.createTriageRule("p", "n", "infra", "Timeout", null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("disabled");
		assertThatThrownBy(() -> tools.ackFailureCluster("p", "java.lang.AssertionError", "known-issue"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("disabled");

		// No project lookup, no mutation, no audit row — the gate is first.
		verifyNoInteractions(reporting, flaky, triage, clustering, access, audit);
	}

}
