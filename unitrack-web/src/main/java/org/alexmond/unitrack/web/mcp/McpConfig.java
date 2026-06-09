package org.alexmond.unitrack.web.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers UniTrack's read-only {@link UniTrackMcpTools} as MCP tools. The
 * {@code spring-ai-starter-mcp-server-webmvc} starter auto-detects this
 * {@link ToolCallbackProvider} and serves the tools over SSE.
 */
@Configuration
public class McpConfig {

	@Bean
	public ToolCallbackProvider unitrackToolCallbackProvider(UniTrackMcpTools tools) {
		return MethodToolCallbackProvider.builder().toolObjects(tools).build();
	}

}
