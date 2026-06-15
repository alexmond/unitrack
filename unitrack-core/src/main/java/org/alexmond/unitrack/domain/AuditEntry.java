package org.alexmond.unitrack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * An append-only record of a state-changing action (today: AI/MCP write tools). Captures
 * who acted, what they did, on which project/target, and through which channel — so every
 * machine-driven mutation is attributable and reviewable.
 */
@Entity
@Table(name = "audit_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Username that performed the action, or null if anonymous. */
	@Column(name = "actor")
	private String actor;

	/** Machine action code, e.g. {@code QUARANTINE_FLAKY}, {@code CREATE_TRIAGE_RULE}. */
	@Column(nullable = false, length = 64)
	private String action;

	/** Channel the action came through, e.g. {@code MCP}. */
	@Column(nullable = false, length = 32)
	private String source;

	/** Project the action targeted, or null if not project-scoped. */
	@Column(name = "project_id")
	private Long projectId;

	/** Human-readable description of the target and outcome. */
	@Column(length = 1024)
	private String detail;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	public AuditEntry(String actor, String action, String source, Long projectId, String detail) {
		this.actor = actor;
		this.action = action;
		this.source = source;
		this.projectId = projectId;
		this.detail = detail;
	}

}
