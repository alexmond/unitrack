package org.alexmond.unitrack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A personal API token. The raw secret is shown once at creation; only its SHA-256 hash
 * is stored. {@code prefix} is a non-secret display fragment.
 */
@Entity
@Table(name = "api_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false)
	private String name;

	@Column(name = "token_hash", nullable = false, unique = true)
	private String tokenHash;

	/** Non-secret display fragment, e.g. {@code ut_ab12cd…}. */
	@Column(nullable = false)
	private String prefix;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Setter
	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	@Setter
	private boolean revoked;

	@Setter
	@Column(name = "expiry_reminded_at")
	private Instant expiryRemindedAt;

	public ApiToken(User user, String name, String tokenHash, String prefix, Instant expiresAt) {
		this.user = user;
		this.name = name;
		this.tokenHash = tokenHash;
		this.prefix = prefix;
		this.expiresAt = expiresAt;
	}

	public boolean isActive() {
		return !revoked && (expiresAt == null || expiresAt.isAfter(Instant.now()));
	}

}
