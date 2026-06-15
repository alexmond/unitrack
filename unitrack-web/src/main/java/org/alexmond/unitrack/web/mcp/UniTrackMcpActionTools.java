package org.alexmond.unitrack.web.mcp;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.FlakyStatus;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.report.FailureClusteringService;
import org.alexmond.unitrack.report.FlakyTestService;
import org.alexmond.unitrack.report.ReportingService;
import org.alexmond.unitrack.report.TriageService;
import org.alexmond.unitrack.web.account.AuditService;
import org.alexmond.unitrack.web.account.ProjectAccessService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * State-changing MCP tools (the "act" half of the copilot). Each tool is
 * human-in-the-loop by design: it is only registered/usable when
 * {@code unitrack.mcp.writes-enabled=true}, requires an authenticated caller with WRITE
 * access to the project (an ACTION- or FULL-scoped token), verifies the target actually
 * exists (no acting on hallucinated tests/clusters), performs the change, and appends an
 * {@code audit_entry} row. Returns a short confirmation string.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UniTrackMcpActionTools {

	private static final String SOURCE = "MCP";

	private static final int ACK_RULE_PRIORITY = 50;

	private final ReportingService reporting;

	private final FlakyTestService flaky;

	private final TriageService triage;

	private final FailureClusteringService clustering;

	private final ProjectAccessService access;

	private final AuditService audit;

	private final McpProperties props;

	@Tool(description = "Quarantine (or resolve/reactivate) a flaky test so its failures stop failing the build. "
			+ "status is QUARANTINED, RESOLVED or ACTIVE. Requires MCP writes to be enabled and write access to the "
			+ "project; the action is recorded in the audit log.")
	public String quarantineFlakyTest(@ToolParam(description = "Project numeric id or exact name") String project,
			@ToolParam(description = "Fully-qualified test class name") String className,
			@ToolParam(description = "Test method name") String name,
			@ToolParam(description = "New status: QUARANTINED, RESOLVED or ACTIVE (default QUARANTINED)",
					required = false) String status,
			@ToolParam(description = "Optional note explaining why", required = false) String note) {
		Project p = requireWritableProject(project);
		boolean known = this.flaky.listFlaky(p.getId())
			.stream()
			.anyMatch((f) -> equalsTest(f.className(), f.name(), className, name));
		if (!known) {
			throw new IllegalArgumentException("No flaky test '" + className + "#" + name + "' is tracked for project '"
					+ p.getName() + "'. List flaky tests first; this tool won't act on an untracked test.");
		}
		FlakyStatus newStatus = parseStatus(status);
		this.flaky.setStatus(p.getId(), className, name, newStatus, note);
		String detail = newStatus + " flaky test " + className + "#" + name + ((note != null) ? " (" + note + ")" : "");
		this.audit.record(this.access.currentUsername(), "QUARANTINE_FLAKY", SOURCE, p.getId(), detail);
		return "Set " + className + "#" + name + " to " + newStatus + " in project '" + p.getName() + "'.";
	}

	@Tool(description = "Create a triage rule that auto-categorizes failures whose text matches a pattern (regex or "
			+ "substring). Requires MCP writes to be enabled and write access to the project; recorded in the audit log.")
	public String createTriageRule(@ToolParam(description = "Project numeric id or exact name") String project,
			@ToolParam(description = "Short rule name") String name,
			@ToolParam(
					description = "Category to assign matching failures, e.g. 'infra' or 'product-bug'") String category,
			@ToolParam(
					description = "Regex or substring matched against the failure type/message/stacktrace") String pattern,
			@ToolParam(description = "Priority; lower wins (default 100)", required = false) Integer priority) {
		Project p = requireWritableProject(project);
		int prio = (priority != null) ? priority : 100;
		this.triage.addRule(p.getId(), name, category, pattern, prio);
		String detail = "rule '" + name + "' -> category '" + category + "' for /" + pattern + "/";
		this.audit.record(this.access.currentUsername(), "CREATE_TRIAGE_RULE", SOURCE, p.getId(), detail);
		return "Created triage rule '" + name + "' (category '" + category + "') in project '" + p.getName() + "'.";
	}

	@Tool(description = "Acknowledge a failure cluster by routing its signature to a triage category, so future "
			+ "matching failures are auto-categorized. Identify the cluster by its failureType (from getFailureClusters). "
			+ "Requires MCP writes to be enabled and write access to the project; recorded in the audit log.")
	public String ackFailureCluster(@ToolParam(description = "Project numeric id or exact name") String project,
			@ToolParam(
					description = "The cluster's failureType signature (from getFailureClusters)") String failureType,
			@ToolParam(description = "Category to assign, e.g. 'known-issue'") String category) {
		Project p = requireWritableProject(project);
		boolean exists = this.clustering.cluster(p.getId())
			.stream()
			.anyMatch((c) -> failureType.equals(c.failureType()));
		if (!exists) {
			throw new IllegalArgumentException("No failure cluster with type '" + failureType + "' in project '"
					+ p.getName() + "'. Call getFailureClusters first; this tool won't act on an unknown cluster.");
		}
		this.triage.addRule(p.getId(), "ack: " + failureType, category, failureType, ACK_RULE_PRIORITY);
		String detail = "acknowledged cluster '" + failureType + "' -> category '" + category + "'";
		this.audit.record(this.access.currentUsername(), "ACK_CLUSTER", SOURCE, p.getId(), detail);
		return "Acknowledged cluster '" + failureType + "' as '" + category + "' in project '" + p.getName() + "'.";
	}

	private Project requireWritableProject(String project) {
		if (!this.props.isWritesEnabled()) {
			throw new IllegalStateException(
					"MCP write actions are disabled on this server (set unitrack.mcp.writes-enabled=true to allow them).");
		}
		Project resolved;
		if (project != null && !project.isBlank() && project.chars().allMatch(Character::isDigit)) {
			resolved = this.reporting.findProject(Long.parseLong(project))
				.orElseThrow(() -> new IllegalArgumentException("No project with id " + project));
		}
		else {
			resolved = this.reporting.findProjectByName(project)
				.orElseThrow(() -> new IllegalArgumentException("No project named '" + project + "'"));
		}
		this.access.requireRead(resolved);
		this.access.requireWrite(resolved);
		return resolved;
	}

	private static FlakyStatus parseStatus(String status) {
		if (status == null || status.isBlank()) {
			return FlakyStatus.QUARANTINED;
		}
		String wanted = status.trim().toUpperCase(java.util.Locale.ROOT);
		for (FlakyStatus s : FlakyStatus.values()) {
			if (s.name().equals(wanted)) {
				return s;
			}
		}
		throw new IllegalArgumentException("Unknown status '" + status + "'. Use QUARANTINED, RESOLVED or ACTIVE.");
	}

	private static boolean equalsTest(String aClass, String aName, String bClass, String bName) {
		return java.util.Objects.equals(aClass, bClass) && java.util.Objects.equals(aName, bName);
	}

}
