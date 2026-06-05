package org.alexmond.unitrack.web.github;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Provides a {@link RestClient.Builder} for the GitHub client. Spring Boot 4 no longer
 * auto-configures one on the default web classpath, so we supply it here.
 */
@Configuration
class GitHubConfig {

	@Bean
	@ConditionalOnMissingBean
	RestClient.Builder unitrackRestClientBuilder() {
		return RestClient.builder();
	}

}
