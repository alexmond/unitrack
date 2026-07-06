package org.alexmond.unitrack.web.github;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * GitHub App credentials, bound from {@code unitrack.github.app.*}. When configured (an
 * {@code appId} plus a private key), UniTrack authenticates as the App with short-lived
 * per-installation tokens instead of the shared PAT ({@code unitrack.github.token}). Left
 * blank, the PAT path is used unchanged — the App is purely additive (#442).
 */
@Component
@ConfigurationProperties(prefix = "unitrack.github.app")
@Getter
@Setter
public class GitHubAppProperties {

	/** The GitHub App's numeric ID (its "App ID" on the App settings page). */
	private Long appId;

	/**
	 * The App's private key, PEM-encoded (from a K8s secret). Must be PKCS#8 —
	 * {@code -----BEGIN PRIVATE KEY-----}; convert GitHub's PKCS#1 download with
	 * {@code openssl pkcs8 -topk8 -nocrypt -in app.pem -out app.pk8}.
	 */
	private String privateKey;

	/**
	 * Optional installation ID to use for every repo. Left blank, the installation is
	 * resolved per repo via {@code GET /repos/{owner}/{repo}/installation} and cached.
	 */
	private Long installationId;

	/** True once an App ID and a private key are both present. */
	public boolean isConfigured() {
		return this.appId != null && this.privateKey != null && !this.privateKey.isBlank();
	}

}
