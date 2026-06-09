package org.alexmond.unitrack.cli;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.RestClient;

/**
 * Wiring for the CLI. Provides a {@link RestClient.Builder} since this non-web app does
 * not get one auto-configured.
 */
@Configuration
class CliConfig {

	private static final long MAX_RETRIES = 2;

	private static final Duration BACKOFF_INITIAL = Duration.ofMillis(400);

	private static final double BACKOFF_MULTIPLIER = 2.0;

	private static final Duration BACKOFF_MAX = Duration.ofSeconds(4);

	@Bean
	@ConditionalOnMissingBean
	RestClient.Builder restClientBuilder() {
		return RestClient.builder();
	}

	/**
	 * Retries transient upload failures (network errors, retryable 5xx) with exponential
	 * backoff.
	 */
	@Bean
	@ConditionalOnMissingBean
	RetryTemplate uploadRetryTemplate() {
		RetryPolicy policy = RetryPolicy.builder()
			.maxRetries(MAX_RETRIES)
			.delay(BACKOFF_INITIAL)
			.multiplier(BACKOFF_MULTIPLIER)
			.maxDelay(BACKOFF_MAX)
			.includes(RetryableUploadException.class)
			.build();
		return new RetryTemplate(policy);
	}

}
