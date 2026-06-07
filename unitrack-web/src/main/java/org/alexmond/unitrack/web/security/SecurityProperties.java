package org.alexmond.unitrack.web.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Security configuration, bound from {@code unitrack.security.*}. */
@Component
@ConfigurationProperties(prefix = "unitrack.security")
@Getter
@Setter
public class SecurityProperties {

	/**
	 * When true (default), all endpoints are permitted without authentication — the live
	 * deploy, CI and uploader keep working. Login still works so users can manage their
	 * profile and tokens. Set false to require login for the UI and a token for the API.
	 */
	private boolean openMode = true;

	/**
	 * When true, {@code POST /api/v1/ingest} requires a valid API token (or session) even
	 * in open mode — so CI uploads must authenticate. Default false for back-compat.
	 */
	private boolean requireIngestToken;

	/** Username of the default admin seeded on first start (when there are no users). */
	private String adminUsername = "admin";

	/** Password for the seeded admin; if blank, a random one is generated and logged. */
	private String adminPassword;

}
