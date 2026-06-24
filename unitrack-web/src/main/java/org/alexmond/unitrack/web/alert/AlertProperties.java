package org.alexmond.unitrack.web.alert;

import java.time.Duration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Alerting configuration, bound from {@code unitrack.alerts.*}. */
@Component
@ConfigurationProperties(prefix = "unitrack.alerts")
@Getter
@Setter
public class AlertProperties {

	/**
	 * Password used to encrypt channel secrets (webhook URLs, signing secrets) at rest.
	 * <strong>Override in production</strong> ({@code UNITRACK_ALERTS_ENCRYPTION_KEY});
	 * the built-in default is for local/dev only and is logged as a warning on startup.
	 */
	private String encryptionKey = "unitrack-dev-insecure-key";

	/** Hex-encoded salt for key derivation. Override alongside the key in production. */
	private String encryptionSalt = "5c0744940b5c369b";

	/** notify4j delivery tuning: async pool + per-channel HTTP timeouts/retry. */
	private final Delivery delivery = new Delivery();

	/**
	 * Webhook-style channel delivery settings, applied when the notify4j factory is built
	 * (#300). Async keeps a slow channel off the ingest thread; retry rides out transient
	 * 5xx/429/IO failures.
	 */
	@Getter
	@Setter
	public static class Delivery {

		/** Threads for async, non-blocking channel delivery (shared across projects). */
		private int poolSize = 4;

		/** Connect timeout for webhook-style channels. */
		private Duration connectTimeout = Duration.ofSeconds(5);

		/** Read timeout for webhook-style channels. */
		private Duration readTimeout = Duration.ofSeconds(10);

		/** Total delivery attempts per send (1 disables retry). */
		private int maxAttempts = 3;

		/** Base backoff between retries (doubled each attempt, capped by notify4j). */
		private Duration retryBackoff = Duration.ofMillis(500);

	}

}
