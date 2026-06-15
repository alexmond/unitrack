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

import java.time.Instant;

/**
 * Maps tests to an owner by matching the test class name. {@code pattern} is a regex
 * (falls back to literal substring if invalid); lower {@code priority} wins, first match
 * assigns the owner. Used to attribute failures/flakes to a person or team.
 */
@Entity
@Table(name = "test_owner_rule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TestOwnerRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	/**
	 * The owner — a free-form team or handle (e.g. {@code @payments} or
	 * {@code platform}).
	 */
	@Column(nullable = false)
	private String owner;

	/** Regex matched against the test class name (e.g. {@code com\.billing\..*}). */
	@Column(nullable = false, length = 2000)
	private String pattern;

	@Column(nullable = false)
	private int priority = 100;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	public TestOwnerRule(Project project, String owner, String pattern, int priority) {
		this.project = project;
		this.owner = owner;
		this.pattern = pattern;
		this.priority = priority;
	}

}
