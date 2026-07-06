package org.alexmond.unitrack.web.github;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Mints short-lived GitHub App installation tokens: signs an RS256 JWT with the App
 * private key, exchanges it at {@code POST /app/installations/{id}/access_tokens}, and
 * caches the resulting token per installation until just before it expires (#442). The
 * installation ID is taken from config or resolved (and cached) per repo. Only used when
 * {@link GitHubAppProperties#isConfigured()} — otherwise the PAT path applies.
 */
@Service
public class GitHubAppTokenService {

	private static final Logger log = LoggerFactory.getLogger(GitHubAppTokenService.class);

	/** JWTs live ≤10 min; use 9 to stay clear of GitHub's ceiling with clock skew. */
	private static final long JWT_TTL_SECONDS = 540;

	/** Backdate {@code iat} to tolerate a slow clock on this host. */
	private static final long JWT_SKEW_SECONDS = 60;

	/** Refresh an installation token this many seconds before its stated expiry. */
	private static final long TOKEN_REFRESH_SKEW_SECONDS = 60;

	private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
	};

	private final GitHubAppProperties app;

	private final GitHubProperties props;

	private final RestClient restClient;

	private final Clock clock;

	private final Map<Long, CachedToken> tokenCache = new ConcurrentHashMap<>();

	private final Map<String, Long> installationCache = new ConcurrentHashMap<>();

	@org.springframework.beans.factory.annotation.Autowired
	public GitHubAppTokenService(GitHubAppProperties app, GitHubProperties props,
			RestClient.Builder restClientBuilder) {
		this(app, props, restClientBuilder, Clock.systemUTC());
	}

	GitHubAppTokenService(GitHubAppProperties app, GitHubProperties props, RestClient.Builder restClientBuilder,
			Clock clock) {
		this.app = app;
		this.props = props;
		this.restClient = restClientBuilder.build();
		this.clock = clock;
	}

	public boolean isConfigured() {
		return this.app.isConfigured();
	}

	/**
	 * A valid installation token for the repo — cached, refetched only once it nears
	 * expiry.
	 */
	public String installationToken(String owner, String repo) {
		try {
			long installationId = resolveInstallationId(owner, repo);
			CachedToken cached = this.tokenCache.get(installationId);
			Instant now = this.clock.instant();
			if (cached != null && now.isBefore(cached.expiresAt().minusSeconds(TOKEN_REFRESH_SKEW_SECONDS))) {
				return cached.token();
			}
			CachedToken fresh = exchange(installationId);
			this.tokenCache.put(installationId, fresh);
			return fresh.token();
		}
		catch (RestClientException | IllegalStateException ex) {
			// Best-effort: a repo the App isn't installed on (404 on resolve), a bad key,
			// or a GitHub outage must never break ingest — skip the post, don't
			// propagate.
			log.warn("No GitHub App token for {}/{} (App not installed, or key/exchange error): {}", owner, repo,
					ex.getMessage());
			return null;
		}
	}

	private long resolveInstallationId(String owner, String repo) {
		if (this.app.getInstallationId() != null) {
			return this.app.getInstallationId();
		}
		return this.installationCache.computeIfAbsent(owner + "/" + repo, (key) -> {
			Map<String, Object> resp = this.restClient.get()
				.uri(this.props.getApiUrl() + "/repos/{owner}/{repo}/installation", owner, repo)
				.headers(this::jwtHeaders)
				.retrieve()
				.body(MAP);
			return ((Number) resp.get("id")).longValue();
		});
	}

	private CachedToken exchange(long installationId) {
		Map<String, Object> resp = this.restClient.post()
			.uri(this.props.getApiUrl() + "/app/installations/{id}/access_tokens", installationId)
			.headers(this::jwtHeaders)
			.retrieve()
			.body(MAP);
		String token = (String) resp.get("token");
		Instant expiresAt = Instant.parse((String) resp.get("expires_at"));
		return new CachedToken(token, expiresAt);
	}

	private void jwtHeaders(HttpHeaders headers) {
		headers.set("Authorization", "Bearer " + jwt());
		headers.set("Accept", "application/vnd.github+json");
		headers.set("X-GitHub-Api-Version", "2022-11-28");
	}

	/** A freshly-signed App JWT (RS256), issued by the App ID. */
	String jwt() {
		Instant now = this.clock.instant();
		long iat = now.getEpochSecond() - JWT_SKEW_SECONDS;
		long exp = now.getEpochSecond() + JWT_TTL_SECONDS;
		String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
		String payload = base64Url(("{\"iat\":" + iat + ",\"exp\":" + exp + ",\"iss\":" + this.app.getAppId() + "}")
			.getBytes(StandardCharsets.UTF_8));
		String signingInput = header + "." + payload;
		byte[] signature = sign(signingInput.getBytes(StandardCharsets.US_ASCII), this.app.getPrivateKey());
		return signingInput + "." + base64Url(signature);
	}

	private static byte[] sign(byte[] input, String privateKeyPem) {
		try {
			Signature rsa = Signature.getInstance("SHA256withRSA");
			rsa.initSign(parsePrivateKey(privateKeyPem));
			rsa.update(input);
			return rsa.sign();
		}
		catch (IllegalStateException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to sign GitHub App JWT: " + ex.getMessage(), ex);
		}
	}

	private static PrivateKey parsePrivateKey(String pem) throws Exception {
		if (pem == null || pem.isBlank()) {
			throw new IllegalStateException("unitrack.github.app.private-key is not set");
		}
		if (pem.contains("BEGIN RSA PRIVATE KEY")) {
			throw new IllegalStateException("GitHub App private key is PKCS#1; convert it with "
					+ "`openssl pkcs8 -topk8 -nocrypt -in app.pem -out app.pk8` and use the PKCS#8 output");
		}
		String der = pem.replace("-----BEGIN PRIVATE KEY-----", "")
			.replace("-----END PRIVATE KEY-----", "")
			.replaceAll("\\s", "");
		byte[] bytes = Base64.getDecoder().decode(der);
		return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
	}

	private static String base64Url(byte[] bytes) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private record CachedToken(String token, Instant expiresAt) {
	}

}
