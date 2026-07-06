package org.alexmond.unitrack.web.webhook;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Inbound SCM webhook config, bound from {@code unitrack.webhooks.*}. One shared secret
 * is used as the GitHub {@code X-Hub-Signature-256} HMAC key and the GitLab
 * {@code X-Gitlab-Token}. Blank (the default) disables the webhook endpoints entirely.
 */
@Component
@ConfigurationProperties(prefix = "unitrack.webhooks")
@Getter
@Setter
public class WebhookProperties {

	/** Shared secret for verifying inbound webhooks; blank disables the endpoints. */
	private String secret = "";

	boolean enabled() {
		return this.secret != null && !this.secret.isBlank();
	}

}
