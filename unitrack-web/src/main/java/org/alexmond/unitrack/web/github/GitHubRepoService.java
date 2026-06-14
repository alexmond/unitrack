package org.alexmond.unitrack.web.github;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Lists the GitHub repositories visible to the configured token, for the "Import from
 * GitHub" provisioning flow. Reuses the same token and REST client as the commit-status
 * integration ({@code unitrack.github.*}).
 */
@Service
public class GitHubRepoService {

	/** Page size requested from the GitHub API (its maximum). */
	private static final int PER_PAGE = 100;

	/** Safety cap on pages followed, so a huge account can't stall the request. */
	private static final int MAX_PAGES = 10;

	/** Pulls the {@code rel="next"} URL out of a GitHub {@code Link} header. */
	private static final Pattern NEXT_LINK = Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");

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
	 * Repositories the token can access, most-recently-updated first, following
	 * pagination up to {@link #MAX_PAGES} pages. {@link RepoList#truncated()} is true if
	 * more remained.
	 */
	public RepoList listRepos() {
		List<GitHubRepo> all = new ArrayList<>();
		URI uri = URI.create(props.getApiUrl() + "/user/repos?per_page=" + PER_PAGE + "&sort=updated");
		boolean truncated = false;
		for (int page = 0; uri != null; page++) {
			if (page >= MAX_PAGES) {
				truncated = true;
				break;
			}
			ResponseEntity<GitHubRepo[]> response = restClient.get()
				.uri(uri)
				.header("Authorization", "Bearer " + props.getToken())
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.retrieve()
				.toEntity(GitHubRepo[].class);
			GitHubRepo[] body = response.getBody();
			if (body != null) {
				all.addAll(Arrays.asList(body));
			}
			uri = nextLink(response.getHeaders().getFirst("Link"));
		}
		return new RepoList(all, truncated);
	}

	private static URI nextLink(String linkHeader) {
		if (linkHeader == null || linkHeader.isBlank()) {
			return null;
		}
		Matcher matcher = NEXT_LINK.matcher(linkHeader);
		return matcher.find() ? URI.create(matcher.group(1)) : null;
	}

	/**
	 * A page set of repositories plus whether the result was capped before all were
	 * fetched.
	 */
	public record RepoList(List<GitHubRepo> repos, boolean truncated) {
	}

}
