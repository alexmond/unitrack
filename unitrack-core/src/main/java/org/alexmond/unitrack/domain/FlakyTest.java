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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * User-controlled state for a flaky test (quarantine / resolution). Flakiness itself is
 * detected live from run history; this row only records the human decision about a test,
 * keyed by project + class + name.
 */
@Entity
@Table(name = "flaky_test", uniqueConstraints = @UniqueConstraint(columnNames = { "project_id", "class_name", "name" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlakyTest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Column(name = "class_name")
	private String className;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FlakyStatus status = FlakyStatus.ACTIVE;

	@Column(length = 1000)
	private String note;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	public FlakyTest(Project project, String className, String name) {
		this.project = project;
		this.className = className;
		this.name = name;
	}

	public void update(FlakyStatus status, String note) {
		this.status = status;
		this.note = note;
		this.updatedAt = Instant.now();
	}

}
