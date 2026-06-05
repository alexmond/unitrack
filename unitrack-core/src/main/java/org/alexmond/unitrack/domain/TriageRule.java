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
 * A user-defined rule that categorizes a failure when its pattern matches the failure
 * text (type + message + stacktrace). Lower {@code priority} wins; first match assigns
 * the category.
 */
@Entity
@Table(name = "triage_rule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TriageRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Column(nullable = false)
	private String name;

	/** Assigned category, e.g. PRODUCT_DEFECT / TEST_DEFECT / INFRASTRUCTURE / FLAKY. */
	@Column(nullable = false)
	private String category;

	/**
	 * Regex (falls back to literal substring if invalid) matched against the failure
	 * text.
	 */
	@Column(nullable = false, length = 2000)
	private String pattern;

	@Column(nullable = false)
	private int priority = 100;

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	public TriageRule(Project project, String name, String category, String pattern, int priority) {
		this.project = project;
		this.name = name;
		this.category = category;
		this.pattern = pattern;
		this.priority = priority;
	}

}
