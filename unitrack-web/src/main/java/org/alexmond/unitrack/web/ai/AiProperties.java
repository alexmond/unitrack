package org.alexmond.unitrack.web.ai;

import java.time.Duration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI analysis configuration, bound from {@code unitrack.ai.*}. **Off by default** —
 * bring-your-own-key: nothing calls an LLM unless {@code enabled} is true and an
 * {@code api-key} is set.
 */
@Component
@ConfigurationProperties(prefix = "unitrack.ai")
@Getter
@Setter
public class AiProperties {

	/**
	 * Master switch; no LLM call happens unless this is true and an api-key is present.
	 */
	private boolean enabled;

	/** Provider id — only {@code anthropic} today; the analyzer is provider-agnostic. */
	private String provider = "anthropic";

	/** API key (env {@code UNITRACK_AI_API_KEY}). */
	private String apiKey;

	/** Model id for failure root-cause analysis. */
	private String model = "claude-sonnet-4-6";

	/** Per-call request timeout. */
	private Duration timeout = Duration.ofSeconds(30);

	/** Whether AI is usable: enabled and a key is configured. */
	public boolean isUsable() {
		return this.enabled && this.apiKey != null && !this.apiKey.isBlank();
	}

}
