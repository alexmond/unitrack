package org.alexmond.unitrack.web.gitlab;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.QualityGateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Posts a commit status and a merge-request note to GitLab — the GitLab mirror of the
 * GitHub integration. Activated when {@code unitrack.gitlab.enabled}, a token is set, and
 * the project's repo URL is a GitLab one. Best-effort: failures are logged, never
 * propagated to ingest.
 */
@Service
public class GitLabService {

	/**
	 * Hidden marker so the MR note is updated in place instead of duplicated each ingest.
	 */
	static final String MR_NOTE_MARKER = "<!-- unitrack-report -->";

	private static final Logger log = LoggerFactory.getLogger(GitLabService.class);

	private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS = new ParameterizedTypeReference<>() {
	};

	private final GitLabProperties props;

	private final GitLabConfigResolver config;

	private final RestClient restClient;

	public GitLabService(GitLabProperties props, GitLabConfigResolver config, RestClient.Builder restClientBuilder) {
		this.props = props;
		this.config = config;
		this.restClient = restClientBuilder.build();
	}

	/** Sets the commit/pipeline status on GitLab for the run's commit. */
	public void publishStatus(TestRun run, QualityGateResult gate, Double coverageDelta) {
		if (!active(run.getProject().getId())) {
			return;
		}
		String path = projectPath(run.getProject().getRepoUrl());
		String sha = run.getCommitSha();
		if (path == null || sha == null || sha.isBlank()) {
			return;
		}
		boolean passed = (gate == null) || gate.passed();
		Map<String, String> body = Map.of("state", passed ? "success" : "failed", "name", this.props.getContext(),
				"description", describe(run, gate, coverageDelta), "target_url",
				this.props.getServerBaseUrl() + "/runs/" + run.getId());
		try {
			this.restClient.post()
				.uri(URI.create(this.props.getApiUrl() + "/projects/" + encode(path) + "/statuses/" + sha))
				.header("PRIVATE-TOKEN", this.props.getToken())
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.toBodilessEntity();
			log.info("Posted GitLab status for {}@{}: {}", path, sha, body.get("state"));
		}
		catch (RuntimeException ex) {
			log.warn("Failed to post GitLab status for {}@{}: {}", path, sha, ex.getMessage());
		}
	}

	/**
	 * Upserts a results note on the run's merge request, when one is associated. The note
	 * carries a hidden marker so repeated ingests update the same note in place rather
	 * than piling up a new note per run.
	 */
	public void publishMrNote(TestRun run, QualityGateResult gate, Double coverageDelta, int newFailures) {
		if (!active(run.getProject().getId()) || !this.props.isMrNote() || run.getPrNumber() == null) {
			return;
		}
		String path = projectPath(run.getProject().getRepoUrl());
		if (path == null) {
			return;
		}
		String notesBase = this.props.getApiUrl() + "/projects/" + encode(path) + "/merge_requests/" + run.getPrNumber()
				+ "/notes";
		Map<String, String> body = Map.of("body", note(run, gate, coverageDelta, newFailures));
		try {
			Long existing = findExistingNote(notesBase);
			if (existing != null) {
				this.restClient.put()
					.uri(URI.create(notesBase + "/" + existing))
					.header("PRIVATE-TOKEN", this.props.getToken())
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.toBodilessEntity();
			}
			else {
				this.restClient.post()
					.uri(URI.create(notesBase))
					.header("PRIVATE-TOKEN", this.props.getToken())
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.toBodilessEntity();
			}
			log.info("Posted GitLab MR note for {} !{} ({})", path, run.getPrNumber(),
					(existing != null) ? "updated" : "created");
		}
		catch (RuntimeException ex) {
			log.warn("Failed to post GitLab MR note for {} !{}: {}", path, run.getPrNumber(), ex.getMessage());
		}
	}

	/**
	 * The id of the existing UniTrack-marked note on this MR, or null if there is none.
	 */
	private Long findExistingNote(String notesBase) {
		List<Map<String, Object>> notes = this.restClient.get()
			.uri(URI.create(notesBase + "?per_page=100"))
			.header("PRIVATE-TOKEN", this.props.getToken())
			.retrieve()
			.body(LIST_OF_MAPS);
		if (notes == null) {
			return null;
		}
		for (Map<String, Object> n : notes) {
			Object b = n.get("body");
			Object id = n.get("id");
			if (b instanceof String s && s.contains(MR_NOTE_MARKER) && id instanceof Number num) {
				return num.longValue();
			}
		}
		return null;
	}

	private boolean active(Long projectId) {
		return this.config.enabled(projectId) && this.props.getToken() != null && !this.props.getToken().isBlank();
	}

	private String describe(TestRun run, QualityGateResult gate, Double coverageDelta) {
		StringBuilder sb = new StringBuilder("Gate ").append((gate != null) ? gate.status() : "n/a");
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
		String s = sb.toString();
		return (s.length() <= 255) ? s : s.substring(0, 255);
	}

	private String note(TestRun run, QualityGateResult gate, Double coverageDelta, int newFailures) {
		String gateStatus = (gate != null) ? gate.status() : "no gate";
		StringBuilder sb = new StringBuilder(MR_NOTE_MARKER).append("\n### UniTrack — ")
			.append(gateStatus)
			.append("\n\n| Metric | Value |\n|---|---|\n| Tests | ")
			.append(run.getPassed())
			.append(" passed, ")
			.append(run.getFailed() + run.getErrors())
			.append(" failed |\n");
		if (run.getLineCoveragePct() != null) {
			sb.append("| Coverage | ").append(String.format(Locale.ROOT, "%.1f%%", run.getLineCoveragePct()));
			if (coverageDelta != null) {
				sb.append(String.format(Locale.ROOT, " (%+.1f pp)", coverageDelta));
			}
			sb.append(" |\n");
		}
		if (newFailures > 0) {
			sb.append("| New failures | ").append(newFailures).append(" |\n");
		}
		return sb.append("\n[View run](")
			.append(this.props.getServerBaseUrl())
			.append("/runs/")
			.append(run.getId())
			.append(')')
			.toString();
	}

	private static String encode(String path) {
		return URLEncoder.encode(path, StandardCharsets.UTF_8);
	}

	/**
	 * Extracts a GitLab project's full path ({@code group/subgroup/project}) from its
	 * repo URL, or null if it isn't a GitLab URL. Handles https and {@code git@} SSH
	 * forms.
	 */
	static String projectPath(String repoUrl) {
		if (repoUrl == null || repoUrl.isBlank() || !repoUrl.toLowerCase(Locale.ROOT).contains("gitlab")) {
			return null;
		}
		String s = repoUrl.trim().replaceFirst("^[a-zA-Z]+://", "").replaceFirst("^git@", "");
		int slash = s.indexOf('/');
		int colon = s.indexOf(':');
		int hostEnd = (colon >= 0 && (slash < 0 || colon < slash)) ? colon : slash;
		if (hostEnd < 0) {
			return null;
		}
		String path = s.substring(hostEnd + 1).replaceFirst("\\.git$", "").replaceAll("^/+", "").replaceAll("/+$", "");
		return path.isBlank() ? null : path;
	}

}
