package org.alexmond.unitrack.web.github;

import java.util.Locale;
import java.util.Map;

import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.QualityGateResult;
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

	private static final Logger log = LoggerFactory.getLogger(GitHubStatusService.class);

	private final GitHubProperties props;

	private final RestClient restClient;

	public GitHubStatusService(GitHubProperties props, RestClient.Builder restClientBuilder) {
		this.props = props;
		this.restClient = restClientBuilder.build();
	}

	/**
	 * Posts a commit status for the run; silently skips when disabled or not applicable.
	 */
	public void publish(TestRun run, QualityGateResult gate, Double coverageDelta) {
		if (!props.isEnabled() || props.getToken() == null || props.getToken().isBlank()) {
			return;
		}
		String[] repo = parseOwnerRepo(run.getProject().getRepoUrl());
		String sha = run.getCommitSha();
		if (repo == null || sha == null || sha.isBlank()) {
			return;
		}

		boolean passed = (gate == null) || gate.passed();
		Map<String, String> body = Map.of("state", passed ? "success" : "failure", "context", props.getContext(),
				"description", describe(run, gate, coverageDelta), "target_url",
				props.getServerBaseUrl() + "/runs/" + run.getId());
		try {
			restClient.post()
				.uri(props.getApiUrl() + "/repos/{owner}/{repo}/statuses/{sha}", repo[0], repo[1], sha)
				.header("Authorization", "Bearer " + props.getToken())
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

	private String describe(TestRun run, QualityGateResult gate, Double coverageDelta) {
		StringBuilder sb = new StringBuilder();
		sb.append("Gate ").append((gate != null) ? gate.status() : "n/a");
		sb.append(" · ")
			.append(run.getPassed())
			.append(" passed, ")
			.append(run.getFailed() + run.getErrors())
			.append(" failed");
		if (run.getLineCoveragePct() != null) {
			sb.append(" · cov ").append(String.format(Locale.ROOT, "%.1f%%", run.getLineCoveragePct()));
			if (coverageDelta != null) {
				sb.append(String.format(Locale.ROOT, " (%+.1fpp)", coverageDelta));
			}
		}
		return (sb.length() <= 140) ? sb.toString() : sb.substring(0, 140);
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
