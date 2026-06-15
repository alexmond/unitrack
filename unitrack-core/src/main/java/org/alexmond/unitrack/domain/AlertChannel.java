package org.alexmond.unitrack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A per-project alert delivery target. Multi-tenant by project (each project routes to
 * its own channels), unlike a single global channel list. The credential ({@code secret})
 * is stored encrypted at rest — the persistence layer never holds it in plaintext.
 * {@code tags} is a comma-separated list of event kinds to route here
 * ({@code GATE_FAILED} etc.); blank means all kinds.
 */
@Entity
@Table(name = "alert_channel")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertChannel {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private AlertChannelType type;

	/** Human-friendly name for the channel, e.g. {@code #builds} or {@code on-call}. */
	@Column(nullable = false)
	private String label;

	/** Non-secret destination: an email address, or a display URL. May be null. */
	@Setter
	@Column(name = "target")
	private String target;

	/** Encrypted credential: the Slack/webhook URL or signing secret. Null for email. */
	@Setter
	@Column(name = "secret", length = 2048)
	private String secret;

	/** Comma-separated event kinds to route here; blank routes all kinds. */
	@Setter
	@Column(name = "tags")
	private String tags;

	@Setter
	private boolean enabled = true;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	public AlertChannel(Project project, AlertChannelType type, String label, String target, String secret,
			String tags) {
		this.project = project;
		this.type = type;
		this.label = label;
		this.target = target;
		this.secret = secret;
		this.tags = tags;
	}

}
