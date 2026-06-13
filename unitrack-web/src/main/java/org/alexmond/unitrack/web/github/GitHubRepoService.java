package org.alexmond.unitrack.web.github;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Lists the GitHub repositories visible to the configured token, for the "Import from
 * GitHub" provisioning flow. Reuses the same token and REST client as the commit-status
 * integration ({@code unitrack.github.*}).
 */
@Service
public class GitHubRepoService {

	private final GitHubProperties props;

	private final RestClient restClient;

	public GitHubRepoService(GitHubProperties props, RestClient.Builder restClientBuilder) {
		this.props = props;
		this.restClient = restClientBuilder.build();
	}

	/** Whether a GitHub token is configured so repos can be listed. */
	public boolean isConfigured() {
		return props.isEnabled() && props.getToken() != null && !props.getToken().isBlank();
	}

	/**
	 * Repositories the token can access, most-recently-updated first. Returns up to 100
	 * (a single API page) — enough for the import picker.
	 */
	public List<GitHubRepo> listRepos() {
		GitHubRepo[] repos = restClient.get()
			.uri(props.getApiUrl() + "/user/repos?per_page=100&sort=updated")
			.header("Authorization", "Bearer " + props.getToken())
			.header("Accept", "application/vnd.github+json")
			.header("X-GitHub-Api-Version", "2022-11-28")
			.retrieve()
			.body(GitHubRepo[].class);
		return (repos != null) ? Arrays.asList(repos) : List.of();
	}

}
