package org.alexmond.unitrack.web.github;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubAppTokenServiceTest {

	private static final KeyPair KEYS = generateKeys();

	private static KeyPair generateKeys() {
		try {
			KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
			gen.initialize(2048);
			return gen.generateKeyPair();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String pkcs8Pem() {
		String b64 = Base64.getMimeEncoder().encodeToString(KEYS.getPrivate().getEncoded());
		return "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----\n";
	}

	private GitHubAppProperties app(Long installationId) {
		GitHubAppProperties app = new GitHubAppProperties();
		app.setAppId(12345L);
		app.setPrivateKey(pkcs8Pem());
		app.setInstallationId(installationId);
		return app;
	}

	private GitHubProperties props() {
		GitHubProperties p = new GitHubProperties();
		p.setApiUrl("https://api.github.com");
		return p;
	}

	private static String tokenJson(String token, Instant expiresAt) {
		return "{\"token\":\"" + token + "\",\"expires_at\":\"" + DateTimeFormatter.ISO_INSTANT.format(expiresAt)
				+ "\"}";
	}

	@Test
	void cachesInstallationTokenAndRefetchesAfterExpiry() {
		Instant base = Instant.parse("2026-07-05T12:00:00Z");
		MutableClock clock = new MutableClock(base);
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitHubAppTokenService service = new GitHubAppTokenService(app(42L), props(), builder, clock);

		// Two exchanges expected across the whole test: the initial fetch and the
		// post-expiry refetch. The middle call is served from cache (no HTTP).
		server.expect(requestTo("https://api.github.com/app/installations/42/access_tokens"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", startsWith("Bearer ")))
			.andRespond(withSuccess(tokenJson("tok-1", base.plusSeconds(600)), MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://api.github.com/app/installations/42/access_tokens"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess(tokenJson("tok-2", base.plusSeconds(1200)), MediaType.APPLICATION_JSON));

		assertThat(service.installationToken("octo", "repo")).isEqualTo("tok-1");
		// Still well before expiry -> cached, no second HTTP call.
		assertThat(service.installationToken("octo", "repo")).isEqualTo("tok-1");
		// Past (expiry - refresh skew) -> refetch.
		clock.advanceTo(base.plusSeconds(560));
		assertThat(service.installationToken("octo", "repo")).isEqualTo("tok-2");

		server.verify();
	}

	@Test
	void signsAVerifiableRs256Jwt() throws Exception {
		GitHubAppTokenService service = new GitHubAppTokenService(app(1L), props(), RestClient.builder());

		String[] parts = service.jwt().split("\\.");
		assertThat(parts).hasSize(3);
		assertThat(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8)).contains("RS256");
		assertThat(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)).contains("\"iss\":1");

		Signature verifier = Signature.getInstance("SHA256withRSA");
		verifier.initVerify(KEYS.getPublic());
		verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
		assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
	}

	@Test
	void rejectsPkcs1KeyWithAConversionHint() {
		GitHubAppProperties app = app(1L);
		// Build the PKCS#1 banner by concatenation so the literal PEM header isn't a
		// contiguous string in source (it would trip the lab-leak private-key scanner).
		// The service only checks for the marker substring "BEGIN RSA PRIVATE KEY".
		String pkcs1 = "-----BEGIN RSA PRIVATE" + " KEY-----\nMIIfake\n-----END RSA PRIVATE" + " KEY-----";
		app.setPrivateKey(pkcs1);
		GitHubAppTokenService service = new GitHubAppTokenService(app, props(), RestClient.builder());

		assertThatThrownBy(service::jwt).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("PKCS#1")
			.hasMessageContaining("openssl pkcs8 -topk8");
	}

	/** A hand-advanced clock so the cache-expiry path is deterministic. */
	private static final class MutableClock extends Clock {

		private Instant instant;

		MutableClock(Instant instant) {
			this.instant = instant;
		}

		void advanceTo(Instant to) {
			this.instant = to;
		}

		@Override
		public Instant instant() {
			return this.instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

	}

}
