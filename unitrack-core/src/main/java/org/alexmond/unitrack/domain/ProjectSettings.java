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
import lombok.Setter;

/**
 * Per-project overrides for quality-gate configuration. Every field is nullable; a null
 * value means "inherit the global {@code unitrack.quality-gate.*} default".
 */
@Entity
@Table(name = "project_settings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectSettings {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "project_id", nullable = false, unique = true)
	private Long projectId;

	@Column(name = "base_branch")
	private String baseBranch;

	@Column(name = "min_line_coverage")
	private Double minLineCoverage;

	@Column(name = "max_coverage_drop_pct")
	private Double maxCoverageDropPct;

	@Column(name = "fail_on_new_failures")
	private Boolean failOnNewFailures;

	@Column(name = "gh_enabled")
	private Boolean ghEnabled;

	@Column(name = "gh_context")
	private String ghContext;

	@Column(name = "gh_pr_comment")
	private Boolean ghPrComment;

	public ProjectSettings(Long projectId) {
		this.projectId = projectId;
	}

}
