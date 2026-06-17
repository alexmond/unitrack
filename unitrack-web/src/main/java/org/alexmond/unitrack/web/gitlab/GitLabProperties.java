package org.alexmond.unitrack.web.gitlab;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * GitLab integration config, bound from {@code unitrack.gitlab.*}. Mirrors the GitHub
 * one.
 */
@Component
@ConfigurationProperties(prefix = "unitrack.gitlab")
@Getter
@Setter
public class GitLabProperties {

	/** Master switch; nothing is posted unless enabled and a token is set. */
	private boolean enabled;

	/** Token with {@code api} scope (project/personal/group access token). */
	private String token;

	/** GitLab REST API base URL (override for self-hosted GitLab). */
	private String apiUrl = "https://gitlab.com/api/v4";

	/**
	 * Public base URL of this UniTrack server, used to build status/note target links.
	 */
	private String serverBaseUrl = "http://localhost:8080";

	/** Commit-status name shown on the pipeline/MR. */
	private String context = "unitrack/quality-gate";

	/** Also post a results note on the associated merge request. */
	private boolean mrNote = true;

}
