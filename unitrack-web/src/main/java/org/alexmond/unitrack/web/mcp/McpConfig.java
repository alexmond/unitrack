package org.alexmond.unitrack.web.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers UniTrack's MCP tools: the read-only {@link UniTrackMcpTools} and the gated,
 * audited {@link UniTrackMcpActionTools} write tools. The
 * {@code spring-ai-starter-mcp-server-webmvc} starter auto-detects this
 * {@link ToolCallbackProvider} and serves the tools over SSE.
 */
@Configuration
public class McpConfig {

	@Bean
	public ToolCallbackProvider unitrackToolCallbackProvider(UniTrackMcpTools tools, UniTrackMcpActionTools actions) {
		return MethodToolCallbackProvider.builder().toolObjects(tools, actions).build();
	}

}
