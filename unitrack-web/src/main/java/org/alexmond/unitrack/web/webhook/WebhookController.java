package org.alexmond.unitrack.web.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.report.BranchCleanupService;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Inbound SCM webhooks that remove a branch's data when it's deleted upstream (#400) —
 * the precise, immediate counterpart to the time-based expiry job. Authenticated by the
 * webhook signature/token (see {@link WebhookProperties}), not by a user session, so the
 * endpoints are permitted in {@code SecurityConfig}. Only branch-delete events act;
 * everything else is acknowledged and ignored.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

	private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final String ZERO_SHA = "0000000000000000000000000000000000000000";

	private final WebhookProperties props;

	private final BranchCleanupService cleanup;

	private final ProjectRepository projects;

	@PostMapping("/github")
	public ResponseEntity<String> github(@RequestHeader(value = "X-GitHub-Event", required = false) String event,
			@RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
			@RequestBody byte[] body) {
		if (!this.props.enabled()) {
			return ResponseEntity.notFound().build();
		}
		if (!validGitHubSignature(signature, body)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("bad signature");
		}
		if (!"delete".equals(event)) {
			return ResponseEntity.ok("ignored");
		}
		JsonNode root = parse(body);
		if (root == null) {
			return ResponseEntity.badRequest().body("invalid payload");
		}
		if (!"branch".equals(text(root, "ref_type"))) {
			return ResponseEntity.ok("ignored");
		}
		return handleDelete(text(root.path("repository"), "full_name"), text(root, "ref"));
	}

	@PostMapping("/gitlab")
	public ResponseEntity<String> gitlab(@RequestHeader(value = "X-Gitlab-Token", required = false) String token,
			@RequestBody byte[] body) {
		if (!this.props.enabled()) {
			return ResponseEntity.notFound().build();
		}
		if (!constantTimeEquals(this.props.getSecret(), token)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("bad token");
		}
		JsonNode root = parse(body);
		if (root == null) {
			return ResponseEntity.badRequest().body("invalid payload");
		}
		// GitLab signals a branch delete with a Push Hook whose `after` is all zeros.
		String ref = text(root, "ref");
		if (!ZERO_SHA.equals(text(root, "after")) || ref == null || !ref.startsWith("refs/heads/")) {
			return ResponseEntity.ok("ignored");
		}
		String branch = ref.substring("refs/heads/".length());
		return handleDelete(text(root.path("project"), "path_with_namespace"), branch);
	}

	private ResponseEntity<String> handleDelete(String repoIdentifier, String branch) {
		if (branch == null || branch.isBlank() || repoIdentifier == null || repoIdentifier.isBlank()) {
			return ResponseEntity.ok("ignored");
		}
		Optional<Project> project = matchProject(repoIdentifier);
		if (project.isEmpty()) {
			log.info("Branch-delete webhook for unknown repo '{}' (branch '{}') — no matching project", repoIdentifier,
					branch);
			return ResponseEntity.ok("no matching project");
		}
		int removed = this.cleanup.deleteBranch(project.get().getId(), branch);
		return ResponseEntity.ok("removed " + Math.max(removed, 0) + " run(s)");
	}

	/**
	 * Finds the project whose repo URL points at the same {@code owner/repo} as the
	 * payload.
	 */
	private Optional<Project> matchProject(String repoIdentifier) {
		String wanted = ownerRepo(repoIdentifier);
		if (wanted == null) {
			return Optional.empty();
		}
		return this.projects.findAll()
			.stream()
			.filter((p) -> p.getRepoUrl() != null && wanted.equals(ownerRepo(p.getRepoUrl())))
			.findFirst();
	}

	/** Last two path segments of a repo URL or {@code owner/repo} string, normalized. */
	static String ownerRepo(String url) {
		if (url == null || url.isBlank()) {
			return null;
		}
		String s = url.trim().toLowerCase(Locale.ROOT).replaceFirst("^[a-z]+://", "").replace(':', '/');
		if (s.endsWith(".git")) {
			s = s.substring(0, s.length() - 4);
		}
		while (s.endsWith("/")) {
			s = s.substring(0, s.length() - 1);
		}
		String[] parts = s.split("/");
		if (parts.length < 2) {
			return null;
		}
		return parts[parts.length - 2] + "/" + parts[parts.length - 1];
	}

	private boolean validGitHubSignature(String signature, byte[] body) {
		if (signature == null || !signature.startsWith("sha256=")) {
			return false;
		}
		String expected = "sha256=" + hmacSha256Hex(this.props.getSecret(), body);
		return constantTimeEquals(expected, signature);
	}

	private static String hmacSha256Hex(String secret, byte[] body) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(body));
		}
		catch (java.security.GeneralSecurityException ex) {
			throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
		}
	}

	private static boolean constantTimeEquals(String a, String b) {
		if (a == null || b == null) {
			return false;
		}
		return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}

	private static JsonNode parse(byte[] body) {
		try {
			return MAPPER.readTree(body);
		}
		catch (RuntimeException ex) {
			return null;
		}
	}

	private static String text(JsonNode node, String field) {
		String value = node.path(field).asString("");
		return value.isBlank() ? null : value;
	}

}
