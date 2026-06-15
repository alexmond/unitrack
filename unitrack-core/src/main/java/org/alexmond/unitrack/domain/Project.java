package org.alexmond.unitrack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A unit of work being tracked — typically one source repository. */
@Entity
@Table(name = "project")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String name;

	@Setter
	@Column(name = "repo_url")
	private String repoUrl;

	/** Who may read this project's data. New projects default to PRIVATE. */
	@Setter
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private Visibility visibility = Visibility.PRIVATE;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	public Project(String name, String repoUrl) {
		this.name = name;
		this.repoUrl = repoUrl;
	}

}
