package org.alexmond.unitrack.web.gitlab;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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

	/**
	 * Cached id of the token's own user (see {@link #selfUserId()}); -1 =
	 * resolved-but-unknown, null = not yet resolved.
	 */
	private final AtomicReference<Long> selfUserId = new AtomicReference<>();

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
		String path = projectPath(run.getProject().getRepoUrl(), hostOf(this.props.getApiUrl()));
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
		String path = projectPath(run.getProject().getRepoUrl(), hostOf(this.props.getApiUrl()));
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
	 * The marker is only trusted on a note authored by the token's own account, so a note
	 * that a third party plants with a copied marker can't hijack the upsert (the marker
	 * is a forgeable HTML comment). When the token's identity can't be resolved, falls
	 * back to marker-only matching so behaviour is never worse than before.
	 */
	private Long findExistingNote(String notesBase) {
		Long self = selfUserId();
		List<Map<String, Object>> notes = this.restClient.get()
			.uri(URI.create(notesBase + "?per_page=100"))
			.header("PRIVATE-TOKEN", this.props.getToken())
			.retrieve()
			.body(LIST_OF_MAPS);
		if (notes == null) {
			return null;
		}
		return notes.stream()
			.filter((n) -> isOwnMarkedNote(n, self))
			.map(GitLabService::noteId)
			.findFirst()
			.orElse(null);
	}

	/**
	 * Whether {@code note} is a UniTrack-marked note that we may update in place: it
	 * carries the marker and is authored by the token's own account ({@code self}), or
	 * {@code self} is unresolved (in which case the marker match is not author-scoped).
	 */
	private static boolean isOwnMarkedNote(Map<String, Object> note, Long self) {
		return note.get("body") instanceof String s && s.contains(MR_NOTE_MARKER) && note.get("id") instanceof Number
				&& (self == null || self.equals(authorId(note)));
	}

	/** The {@code id} of a note object as a long, or null when absent/non-numeric. */
	private static Long noteId(Map<String, Object> note) {
		return (note.get("id") instanceof Number n) ? n.longValue() : null;
	}

	/** The {@code author.id} of a note object, or null when absent. */
	private static Long authorId(Map<String, Object> note) {
		if (note.get("author") instanceof Map<?, ?> author && author.get("id") instanceof Number n) {
			return n.longValue();
		}
		return null;
	}

	/**
	 * The token's own GitLab user id (via {@code GET /user}), resolved once and cached,
	 * or null when it can't be determined (e.g. a group/CI token) — in which case the
	 * marker match is not author-scoped.
	 */
	private Long selfUserId() {
		Long cached = this.selfUserId.get();
		if (cached != null) {
			return (cached < 0) ? null : cached;
		}
		Long resolved = null;
		try {
			Map<?, ?> me = this.restClient.get()
				.uri(URI.create(this.props.getApiUrl() + "/user"))
				.header("PRIVATE-TOKEN", this.props.getToken())
				.retrieve()
				.body(Map.class);
			if (me != null && me.get("id") instanceof Number n) {
				resolved = n.longValue();
			}
		}
		catch (RuntimeException ex) {
			log.debug("Could not resolve GitLab token identity: {}", ex.getMessage());
		}
		this.selfUserId.set((resolved != null) ? resolved : -1L);
		return resolved;
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
	 * repo URL, or null if the URL's host is not {@code host} (the configured GitLab
	 * host). Handles https and {@code git@} SSH forms, and any embedded
	 * {@code user[:pw]@} credentials. Keying on the configured host (rather than a
	 * substring like "gitlab") means self-hosted GitLab on a custom domain is recognised
	 * and non-GitLab hosts are rejected.
	 */
	static String projectPath(String repoUrl, String host) {
		if (repoUrl == null || repoUrl.isBlank() || host == null || host.isBlank()) {
			return null;
		}
		// Strip scheme (https://, git+ssh://, ...) then any leading credentials (git@,
		// oauth2:token@).
		String s = repoUrl.trim().replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "").replaceFirst("^[^@/]+@", "");
		int slash = s.indexOf('/');
		int colon = s.indexOf(':');
		int hostEnd = (colon >= 0 && (slash < 0 || colon < slash)) ? colon : slash;
		if (hostEnd < 0) {
			return null;
		}
		String urlHost = s.substring(0, hostEnd);
		if (!urlHost.equalsIgnoreCase(host)) {
			return null;
		}
		String path = s.substring(hostEnd + 1).replaceFirst("\\.git$", "").replaceAll("^/+", "").replaceAll("/+$", "");
		return path.isBlank() ? null : path;
	}

	/**
	 * The host of the configured GitLab API URL
	 * ({@code https://gitlab.example.com/api/v4} -&gt; {@code gitlab.example.com}, port
	 * stripped), used to recognise which repo URLs this integration owns. Null when the
	 * API URL is unset.
	 */
	static String hostOf(String apiUrl) {
		if (apiUrl == null || apiUrl.isBlank()) {
			return null;
		}
		String s = apiUrl.trim().replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
		int slash = s.indexOf('/');
		String host = (slash >= 0) ? s.substring(0, slash) : s;
		int port = host.indexOf(':');
		if (port >= 0) {
			host = host.substring(0, port);
		}
		return host.isBlank() ? null : host;
	}

}
