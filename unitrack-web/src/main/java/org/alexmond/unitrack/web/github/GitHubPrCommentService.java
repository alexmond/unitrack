package org.alexmond.unitrack.web.github;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.alexmond.unitrack.domain.PerfRun;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.PerfRunRegression;
import org.alexmond.unitrack.report.QualityGateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Posts (and updates) a results-table comment on the pull request associated with a run's
 * commit, so the test/coverage summary shows up inline on every PR. Best-effort: any
 * failure is logged, never propagated to the ingest path.
 */
@Service
public class GitHubPrCommentService {

	/** Hidden marker so we update our own comment instead of posting duplicates. */
	static final String MARKER = "<!-- unitrack-report -->";

	/**
	 * Separate marker for the load-test comment, so it upserts independently of the test
	 * report.
	 */
	static final String PERF_MARKER = "<!-- unitrack-perf-report -->";

	private static final Logger log = LoggerFactory.getLogger(GitHubPrCommentService.class);

	private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS = new ParameterizedTypeReference<>() {
	};

	private final GitHubProperties props;

	private final RestClient restClient;

	private final GitHubConfigResolver config;

	private final GitHubAuth auth;

	public GitHubPrCommentService(GitHubProperties props, RestClient.Builder restClientBuilder,
			GitHubConfigResolver config, GitHubAuth auth) {
		this.props = props;
		this.restClient = restClientBuilder.build();
		this.config = config;
		this.auth = auth;
	}

	/**
	 * Upserts the PR comment for the run; silently skips when disabled or not applicable.
	 */
	public void publish(TestRun run, QualityGateResult gate, Double coverageDelta, int newFailures, int slowerTests) {
		upsert(run.getProject().getId(), run.getProject().getRepoUrl(), run.getCommitSha(), MARKER,
				() -> render(run, gate, coverageDelta, newFailures, slowerTests));
	}

	/**
	 * Upserts the load-test PR comment for a perf run; silently skips when disabled or
	 * not applicable.
	 */
	public void publishPerf(PerfRun run, PerfRunRegression regression) {
		upsert(run.getProject().getId(), run.getProject().getRepoUrl(), run.getCommitSha(), PERF_MARKER,
				() -> renderPerf(run, regression));
	}

	/**
	 * Shared comment upsert: resolve the PR for the commit, then create or update the
	 * marked comment.
	 */
	private void upsert(Long projectId, String repoUrl, String sha, String marker,
			java.util.function.Supplier<String> body) {
		GitHubConfigResolver.Effective cfg = this.config.effective(projectId);
		if (!cfg.enabled() || !cfg.prComment()) {
			return;
		}
		String[] repo = GitHubStatusService.parseOwnerRepo(repoUrl);
		if (repo == null || isBlank(sha)) {
			return;
		}
		String bearer = this.auth.bearerToken(repo[0], repo[1]);
		if (bearer == null) {
			return;
		}
		try {
			Integer pr = resolvePullNumber(repo, sha, bearer);
			if (pr == null) {
				log.debug("No open PR for {}/{}@{}; skipping comment", repo[0], repo[1], sha);
				return;
			}
			String rendered = body.get();
			Long existing = findExistingComment(repo, pr, marker, bearer);
			if (existing != null) {
				updateComment(repo, existing, rendered, bearer);
			}
			else {
				createComment(repo, pr, rendered, bearer);
			}
			log.info("Posted UniTrack PR comment on {}/{}#{}", repo[0], repo[1], pr);
		}
		catch (RuntimeException ex) {
			log.warn("Failed to post PR comment for {}/{}@{}: {}", repo[0], repo[1], sha, ex.getMessage());
		}
	}

	private Integer resolvePullNumber(String[] repo, String sha, String bearer) {
		List<Map<String, Object>> pulls = this.restClient.get()
			.uri(this.props.getApiUrl() + "/repos/{owner}/{repo}/commits/{sha}/pulls", repo[0], repo[1], sha)
			.headers((h) -> authHeaders(h, bearer))
			.retrieve()
			.body(LIST_OF_MAPS);
		if (pulls == null || pulls.isEmpty()) {
			return null;
		}
		Object number = pulls.get(0).get("number");
		return (number instanceof Number n) ? n.intValue() : null;
	}

	private Long findExistingComment(String[] repo, int pr, String marker, String bearer) {
		List<Map<String, Object>> comments = this.restClient.get()
			.uri(this.props.getApiUrl() + "/repos/{owner}/{repo}/issues/{pr}/comments", repo[0], repo[1], pr)
			.headers((h) -> authHeaders(h, bearer))
			.retrieve()
			.body(LIST_OF_MAPS);
		if (comments == null) {
			return null;
		}
		for (Map<String, Object> comment : comments) {
			Object body = comment.get("body");
			Object id = comment.get("id");
			if (body instanceof String s && s.contains(marker) && id instanceof Number n) {
				return n.longValue();
			}
		}
		return null;
	}

	private void createComment(String[] repo, int pr, String body, String bearer) {
		this.restClient.post()
			.uri(this.props.getApiUrl() + "/repos/{owner}/{repo}/issues/{pr}/comments", repo[0], repo[1], pr)
			.headers((h) -> authHeaders(h, bearer))
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("body", body))
			.retrieve()
			.toBodilessEntity();
	}

	private void updateComment(String[] repo, long commentId, String body, String bearer) {
		this.restClient.patch()
			.uri(this.props.getApiUrl() + "/repos/{owner}/{repo}/issues/comments/{id}", repo[0], repo[1], commentId)
			.headers((h) -> authHeaders(h, bearer))
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("body", body))
			.retrieve()
			.toBodilessEntity();
	}

	private void authHeaders(org.springframework.http.HttpHeaders headers, String bearer) {
		headers.set("Authorization", "Bearer " + bearer);
		headers.set("Accept", "application/vnd.github+json");
		headers.set("X-GitHub-Api-Version", "2022-11-28");
	}

	String render(TestRun run, QualityGateResult gate, Double coverageDelta, int newFailures, int slowerTests) {
		boolean passed = (gate == null) || gate.passed();
		String heading = passed ? "✅ gate passed" : "❌ gate failed";
		StringBuilder sb = new StringBuilder(MARKER + "\n## UniTrack — ");
		sb.append(heading).append("\n\n| Metric | Value |\n|---|---|\n| Tests | ");
		sb.append(run.getPassed())
			.append(" passed · ")
			.append(run.getFailed() + run.getErrors())
			.append(" failed · ")
			.append(run.getSkipped())
			.append(" skipped (")
			.append(run.getTotalTests())
			.append(" total) |\n");
		if (run.getLineCoveragePct() != null) {
			sb.append("| Coverage | ").append(String.format(Locale.ROOT, "%.1f%%", run.getLineCoveragePct()));
			if (coverageDelta != null) {
				sb.append(String.format(Locale.ROOT, " (%+.1fpp vs base)", coverageDelta));
			}
			sb.append(" |\n");
		}
		sb.append("| Quality gate | ").append((gate != null) ? gate.status() : "n/a").append(" |\n");
		if (newFailures > 0) {
			sb.append("| New failures | ").append(newFailures).append(" |\n");
		}
		if (slowerTests > 0) {
			sb.append("| Slower tests | ").append(slowerTests).append(" |\n");
		}
		sb.append("\n[View run →](")
			.append(this.props.getServerBaseUrl())
			.append("/runs/")
			.append(run.getId())
			.append(")\n");
		if (run.getPrNumber() != null) {
			sb.append("[View pull request →](")
				.append(this.props.getServerBaseUrl())
				.append("/projects/")
				.append(run.getProject().getId())
				.append("/pr/")
				.append(run.getPrNumber())
				.append(")\n");
		}
		return sb.toString();
	}

	String renderPerf(PerfRun run, PerfRunRegression regression) {
		boolean passed = (regression == null) || regression.passed();
		String heading = passed ? "✅ perf gate passed" : "❌ perf gate failed";
		StringBuilder sb = new StringBuilder(PERF_MARKER + "\n## UniTrack — load test — ");
		sb.append(heading)
			.append(String.format(Locale.ROOT, "%n%n| Metric | Value |%n|---|---|%n"
					+ "| p95 latency | %.0f ms |%n| Throughput | %.1f rps |%n| Error rate | %.2f%% |%n| Samples | %d |%n",
					run.getP95Ms(), run.getThroughputRps(), run.getErrorPct(), run.getSampleCount()));
		if (regression != null) {
			String baselineRef = regression.baselineFound() ? " #" + regression.baselineRunId() : "";
			sb.append("\n### Gate vs baseline")
				.append(baselineRef)
				.append("\n\n| Rule | Status | Detail |\n|---|---|---|\n");
			for (PerfRunRegression.Rule rule : regression.rules()) {
				sb.append("| ")
					.append(rule.name())
					.append(" | ")
					.append(rule.passed() ? "✅" : "❌")
					.append(" | ")
					.append(rule.detail())
					.append(" |\n");
			}
			if (!regression.baselineFound()) {
				sb.append("\n_No prior run on `")
					.append(regression.baseBranch())
					.append("` to compare against — error-rate rule only._\n");
			}
		}
		sb.append("\n[View perf run →](")
			.append(this.props.getServerBaseUrl())
			.append("/perf-runs/")
			.append(run.getId())
			.append(")\n");
		return sb.toString();
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

}
