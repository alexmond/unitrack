package org.alexmond.unitrack.web.account;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.ApiToken;
import org.alexmond.unitrack.domain.TokenScope;
import org.alexmond.unitrack.domain.User;
import org.alexmond.unitrack.repository.ApiTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Personal API tokens: minting (raw secret shown once), lookup/auth, listing, revocation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApiTokenService {

	private static final String PREFIX = "ut_";

	private static final SecureRandom RANDOM = new SecureRandom();

	private final ApiTokenRepository tokens;

	@Transactional
	public Minted create(User user, String name, Instant expiresAt, TokenScope scope) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		String raw = PREFIX + secret;
		String display = PREFIX + secret.substring(0, 6) + "…";
		ApiToken token = tokens.save(new ApiToken(user, name, hash(raw), display, expiresAt, scope));
		return new Minted(token, raw);
	}

	/**
	 * Authenticates a raw token, returning its owner + scope if active. Updates
	 * last-used.
	 */
	@Transactional
	public Optional<Authenticated> authenticate(String rawToken) {
		if (rawToken == null || !rawToken.startsWith(PREFIX)) {
			return Optional.empty();
		}
		return tokens.findByTokenHash(hash(rawToken)).filter(ApiToken::isActive).map((token) -> {
			token.setLastUsedAt(Instant.now());
			User user = token.getUser();
			// Initialize within the transaction so the auth filter can read it (no OSIV
			// there).
			user.getUsername();
			user.getRole();
			return new Authenticated(user, token.getScope());
		});
	}

	public List<ApiToken> listForUser(Long userId) {
		return tokens.findByUserIdOrderByCreatedAtDesc(userId);
	}

	@Transactional
	public void revoke(Long tokenId, Long ownerUserId) {
		tokens.findById(tokenId)
			.filter((t) -> t.getUser().getId().equals(ownerUserId))
			.ifPresent((t) -> t.setRevoked(true));
	}

	private static String hash(String raw) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
		}
		catch (java.security.NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

	/** Result of minting a token — the raw secret is returned only here, never stored. */
	public record Minted(ApiToken token, String rawToken) {
	}

	/** An authenticated token: its owner and what it's scoped to do. */
	public record Authenticated(User user, TokenScope scope) {
	}

}
