package org.alexmond.unitrack.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A user's membership of a project, with a per-project {@link ProjectRole}. */
@Entity
@Table(name = "project_membership", uniqueConstraints = @UniqueConstraint(columnNames = { "project_id", "user_id" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMembership {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "project_id", nullable = false)
	private Long projectId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Setter
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ProjectRole role;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	public ProjectMembership(Long projectId, Long userId, ProjectRole role) {
		this.projectId = projectId;
		this.userId = userId;
		this.role = role;
	}

}
