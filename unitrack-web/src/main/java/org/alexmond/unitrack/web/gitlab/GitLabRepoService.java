package org.alexmond.unitrack.web.gitlab;

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
 * Lists the GitLab projects the configured token is a member of, for the "Import from
 * GitLab" flow. Reuses the same token and API URL as the commit-status/MR-note
 * integration ({@code unitrack.gitlab.*}) — the GitLab mirror of
 * {@code GitHubRepoService}.
 */
@Service
public class GitLabRepoService {

	/** Page size requested from the GitLab API (its maximum). */
	private static final int PER_PAGE = 100;

	/** Safety cap on pages followed, so a huge account can't stall the request. */
	private static final int MAX_PAGES = 10;

	/** Pulls the {@code rel="next"} URL out of a GitLab {@code Link} header. */
	private static final Pattern NEXT_LINK = Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");

	private final GitLabProperties props;

	private final RestClient restClient;

	public GitLabRepoService(GitLabProperties props, RestClient.Builder restClientBuilder) {
		this.props = props;
		this.restClient = restClientBuilder.build();
	}

	/** Whether a GitLab token is configured so projects can be listed. */
	public boolean isConfigured() {
		return this.props.isEnabled() && this.props.getToken() != null && !this.props.getToken().isBlank();
	}

	/**
	 * Projects the token is a member of, most-recently-active first, following pagination
	 * up to {@link #MAX_PAGES} pages. {@link RepoList#truncated()} is true if more
	 * remained.
	 */
	public RepoList listRepos() {
		List<GitLabRepo> all = new ArrayList<>();
		URI uri = URI.create(this.props.getApiUrl()
				+ "/projects?membership=true&order_by=last_activity_at&sort=desc&per_page=" + PER_PAGE);
		boolean truncated = false;
		for (int page = 0; uri != null; page++) {
			if (page >= MAX_PAGES) {
				truncated = true;
				break;
			}
			ResponseEntity<GitLabRepo[]> response = this.restClient.get()
				.uri(uri)
				.header("PRIVATE-TOKEN", this.props.getToken())
				.retrieve()
				.toEntity(GitLabRepo[].class);
			GitLabRepo[] body = response.getBody();
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
	 * A page set of projects plus whether the result was capped before all were fetched.
	 */
	public record RepoList(List<GitLabRepo> repos, boolean truncated) {
	}

}
