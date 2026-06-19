package org.alexmond.unitrack.cli;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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
		return RestClient.builder().requestFactory(freshConnectionFactory());
	}

	/**
	 * A request factory that uses a brand-new HTTP/1.1 client — and therefore a fresh
	 * connection — per request. Large uploads are sharded into several rapid sequential
	 * POSTs; reusing one keep-alive connection across them is what a proxy/CDN drops
	 * mid-batch (RST_STREAM on HTTP/2, EOF on HTTP/1.1). A fresh connection per request
	 * matches the single-upload path, which is reliable.
	 */
	private static ClientHttpRequestFactory freshConnectionFactory() {
		return (uri, httpMethod) -> {
			HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
			return new JdkClientHttpRequestFactory(http).createRequest(uri, httpMethod);
		};
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
