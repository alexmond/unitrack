package org.alexmond.unitrack.web.account;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.ShareLink;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.User;
import org.alexmond.unitrack.repository.ShareLinkRepository;
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
 * Public share links to a run: minting (raw token shown once), resolving the run behind
 * an active token, listing per run, and revocation. The token is the capability —
 * resolution deliberately does not consult project visibility.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShareLinkService {

	private static final String PREFIX = "sh_";

	private static final SecureRandom RANDOM = new SecureRandom();

	private final ShareLinkRepository links;

	@Transactional
	public Minted create(TestRun run, User createdBy) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		String raw = PREFIX + secret;
		String display = PREFIX + secret.substring(0, 6) + "…";
		ShareLink link = links.save(new ShareLink(run, hash(raw), display, createdBy));
		return new Minted(link, raw);
	}

	/** Resolves the run behind an active token (updating last-used); empty otherwise. */
	@Transactional
	public Optional<TestRun> resolve(String rawToken) {
		if (rawToken == null || !rawToken.startsWith(PREFIX)) {
			return Optional.empty();
		}
		return links.findByTokenHash(hash(rawToken)).filter(ShareLink::isActive).map((link) -> {
			link.setLastUsedAt(Instant.now());
			return link.getRun();
		});
	}

	public List<ShareLink> listForRun(Long runId) {
		return links.findByRunIdAndRevokedFalseOrderByCreatedAtDesc(runId);
	}

	@Transactional
	public void revoke(Long linkId) {
		links.findById(linkId).ifPresent((link) -> link.setRevoked(true));
	}

	public Optional<ShareLink> find(Long linkId) {
		return links.findById(linkId);
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

	/**
	 * Result of minting a share link — the raw token is returned only here, never stored.
	 */
	public record Minted(ShareLink link, String rawToken) {
	}

}
