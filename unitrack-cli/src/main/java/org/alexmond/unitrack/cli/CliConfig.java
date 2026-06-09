package org.alexmond.unitrack.cli;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wiring for the CLI. Provides a {@link RestClient.Builder} since this non-web app does
 * not get one auto-configured.
 */
@Configuration
class CliConfig {

	@Bean
	@ConditionalOnMissingBean
	RestClient.Builder restClientBuilder() {
		return RestClient.builder();
	}

}
