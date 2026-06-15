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
 * A public, revocable share link to a single run. Anyone holding the link gets a
 * read-only view regardless of the project's visibility — the token itself is the
 * capability. The raw token is shown once at creation; only its SHA-256 hash is stored.
 */
@Entity
@Table(name = "share_link")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShareLink {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "run_id", nullable = false)
	private TestRun run;

	@Column(name = "token_hash", nullable = false, unique = true)
	private String tokenHash;

	/** Non-secret display fragment, e.g. {@code sh_ab12cd…}. */
	@Column(nullable = false)
	private String prefix;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Setter
	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	@Setter
	private boolean revoked;

	public ShareLink(TestRun run, String tokenHash, String prefix, User createdBy) {
		this.run = run;
		this.tokenHash = tokenHash;
		this.prefix = prefix;
		this.createdBy = createdBy;
	}

	public boolean isActive() {
		return !this.revoked;
	}

}
