package org.alexmond.unitrack.web.mcp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** UniTrack MCP-server options, bound from {@code unitrack.mcp.*}. */
@Component
@ConfigurationProperties(prefix = "unitrack.mcp")
@Getter
@Setter
public class McpProperties {

	/**
	 * When true, the MCP server exposes state-changing action tools (quarantine a flaky
	 * test, acknowledge a failure cluster, create a triage rule). Off by default: even an
	 * authorized user can't mutate data over MCP until an operator opts in. Each action
	 * is still authorized per project and appended to the audit log.
	 */
	private boolean writesEnabled;

}
