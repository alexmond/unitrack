package org.alexmond.unitrack.web.github;

import java.util.Map;

import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.QualityGateResult;
import org.alexmond.unitrack.web.scm.GateSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Publishes a GitHub commit status summarising a run's quality gate, so the result shows
 * up on the commit and any associated pull request. Best-effort: failures are logged,
 * never propagated to the ingest path.
 */
@Service
public class GitHubStatusService {

	/** GitHub truncates a commit-status description beyond this. */
	private static final int MAX_DESCRIPTION = 140;

	private static final Logger log = LoggerFactory.getLogger(GitHubStatusService.class);

	private final GitHubProperties props;

	private final RestClient restClient;

	private final GitHubConfigResolver config;

	private final GitHubAuth auth;

	public GitHubStatusService(GitHubProperties props, RestClient.Builder restClientBuilder,
			GitHubConfigResolver config, GitHubAuth auth) {
		this.props = props;
		this.restClient = restClientBuilder.build();
		this.config = config;
		this.auth = auth;
	}

	/**
	 * Posts a commit status for the run; silently skips when disabled or not applicable.
	 */
	public void publish(TestRun run, QualityGateResult gate, Double coverageDelta) {
		GitHubConfigResolver.Effective cfg = config.effective(run.getProject().getId());
		if (!cfg.enabled()) {
			return;
		}
		String[] repo = parseOwnerRepo(run.getProject().getRepoUrl());
		String sha = run.getCommitSha();
		if (repo == null || sha == null || sha.isBlank()) {
			return;
		}
		String bearer = auth.bearerToken(repo[0], repo[1]);
		if (bearer == null) {
			return;
		}

		boolean passed = (gate == null) || gate.passed();
		Map<String, String> body = Map.of("state", passed ? "success" : "failure", "context", cfg.context(),
				"description", GateSummary.describe(run, gate, coverageDelta, MAX_DESCRIPTION), "target_url",
				props.getServerBaseUrl() + "/runs/" + run.getId());
		try {
			restClient.post()
				.uri(props.getApiUrl() + "/repos/{owner}/{repo}/statuses/{sha}", repo[0], repo[1], sha)
				.header("Authorization", "Bearer " + bearer)
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.toBodilessEntity();
			log.info("Posted GitHub status for {}/{}@{}: {}", repo[0], repo[1], sha, body.get("state"));
		}
		catch (RuntimeException ex) {
			log.warn("Failed to post GitHub status for {}/{}@{}: {}", repo[0], repo[1], sha, ex.getMessage());
		}
	}

	/**
	 * Extracts {@code [owner, repo]} from a GitHub URL, or null if it is not parseable.
	 */
	static String[] parseOwnerRepo(String repoUrl) {
		if (repoUrl == null || repoUrl.isBlank()) {
			return null;
		}
		String path = repoUrl.trim();
		int host = path.indexOf("github.com");
		if (host < 0) {
			return null;
		}
		path = path.substring(host + "github.com".length());
		path = path.replaceFirst("^[:/]+", "");
		if (path.endsWith(".git")) {
			path = path.substring(0, path.length() - 4);
		}
		path = path.replaceAll("/+$", "");
		String[] parts = path.split("/");
		if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
			return null;
		}
		return new String[] { parts[0], parts[1] };
	}

}
