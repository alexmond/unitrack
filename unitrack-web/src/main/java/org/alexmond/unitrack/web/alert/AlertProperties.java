package org.alexmond.unitrack.web.alert;

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

}
