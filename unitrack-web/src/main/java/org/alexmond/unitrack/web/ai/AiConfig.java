package org.alexmond.unitrack.web.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link AiAnalyzer}: the in-app Anthropic analyzer when
 * {@code unitrack.ai.enabled=true} and a key is set, otherwise a no-op. Provider-agnostic
 * — an MCP-delegated analyzer can be added as another conditional bean later.
 */
@Configuration
@Slf4j
class AiConfig {

	@Bean
	@ConditionalOnProperty(prefix = "unitrack.ai", name = "enabled", havingValue = "true")
	AiAnalyzer anthropicAiAnalyzer(AiProperties props) {
		if (!props.isUsable()) {
			log.warn("unitrack.ai.enabled=true but no api-key set — AI analysis disabled");
			return new DisabledAiAnalyzer();
		}
		log.info("AI analysis enabled (provider={}, model={})", props.getProvider(), props.getModel());
		return new AnthropicAiAnalyzer(props);
	}

	@Bean
	@ConditionalOnMissingBean(AiAnalyzer.class)
	AiAnalyzer disabledAiAnalyzer() {
		return new DisabledAiAnalyzer();
	}

}
