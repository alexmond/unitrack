package org.alexmond.unitrack.web.github;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for publishing commit statuses to GitHub, bound from
 * {@code unitrack.github.*}. Disabled by default.
 */
@Component
@ConfigurationProperties(prefix = "unitrack.github")
@Getter
@Setter
public class GitHubProperties {

	/** Master switch; nothing is posted unless enabled and a token is set. */
	private boolean enabled;

	/** Token with {@code repo:status} scope (PAT or GitHub App installation token). */
	private String token;

	/** GitHub REST API base URL (override for GitHub Enterprise). */
	private String apiUrl = "https://api.github.com";

	/** Public base URL of this UniTrack server, used to build status target links. */
	private String serverBaseUrl = "http://localhost:8080";

	/** Status context label shown on the commit/PR. */
	private String context = "unitrack/quality-gate";

	/** Also post/update a results-table comment on the associated pull request. */
	private boolean prComment = true;

}
