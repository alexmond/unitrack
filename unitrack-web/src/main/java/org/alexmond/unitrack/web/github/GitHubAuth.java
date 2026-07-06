package org.alexmond.unitrack.web.github;

import org.springframework.stereotype.Component;

/**
 * The single place that decides how UniTrack authenticates to GitHub for a given repo: a
 * GitHub App installation token when the App is configured, otherwise the shared PAT
 * ({@code unitrack.github.token}). The posting services ({@link GitHubStatusService},
 * {@link GitHubPrCommentService}) resolve their bearer token here so App-vs-PAT selection
 * lives in one spot and PAT-only deployments behave exactly as before (#442).
 */
@Component
public class GitHubAuth {

	private final GitHubProperties props;

	private final GitHubAppTokenService appTokens;

	public GitHubAuth(GitHubProperties props, GitHubAppTokenService appTokens) {
		this.props = props;
		this.appTokens = appTokens;
	}

	/** A PAT-only auth (App never consulted) — for tests and PAT-mode call sites. */
	public static GitHubAuth patOnly(GitHubProperties props) {
		return new GitHubAuth(props, null);
	}

	/**
	 * The bearer token to authenticate a call about {@code owner/repo}, or null when
	 * nothing is configured (caller should skip posting). Prefers a GitHub App
	 * installation token; falls back to the PAT.
	 */
	public String bearerToken(String owner, String repo) {
		if (this.appTokens != null && this.appTokens.isConfigured()) {
			return this.appTokens.installationToken(owner, repo);
		}
		String token = this.props.getToken();
		return (token != null && !token.isBlank()) ? token : null;
	}

}
