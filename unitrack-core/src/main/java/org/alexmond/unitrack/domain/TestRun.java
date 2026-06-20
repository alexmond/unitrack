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

/** One ingestion of test results (and optionally coverage) for a project at a commit. */
@Entity
@Table(name = "test_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TestRun {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	private String branch;

	/** Coverage flag / component (e.g. frontend, backend); "default" when unset. */
	@Column(nullable = false)
	private String flag = "default";

	@Column(name = "commit_sha")
	private String commitSha;

	@Column(name = "build_url")
	private String buildUrl;

	/**
	 * Friendly build identifier from the CI run (e.g. GitHub's run number), shown as
	 * "build #N".
	 */
	@Setter
	@Column(name = "build_name")
	private String buildName;

	@Column(name = "ci_provider")
	private String ciProvider;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "total_tests", nullable = false)
	private int totalTests;

	@Column(nullable = false)
	private int passed;

	@Column(nullable = false)
	private int failed;

	@Column(nullable = false)
	private int errors;

	@Column(nullable = false)
	private int skipped;

	@Column(name = "duration_ms", nullable = false)
	private long durationMs;

	/** PASSED if there are no failures/errors, otherwise FAILED. */
	@Column(nullable = false)
	private String status = "PASSED";

	/** Line coverage percentage 0-100, null when no coverage was uploaded. */
	@Setter
	@Column(name = "line_coverage_pct")
	private Double lineCoveragePct;

	@Setter
	@Column(name = "branch_coverage_pct")
	private Double branchCoveragePct;

	/** Merge key for sharded uploads (e.g. a CI build id); null = standalone run. */
	@Setter
	@Column(name = "run_key")
	private String runKey;

	/**
	 * For a pull/merge-request build: the target branch the change merges into; null
	 * otherwise.
	 */
	@Setter
	@Column(name = "base_branch")
	private String baseBranch;

	/** Pull/merge-request number this run belongs to; null for ordinary branch builds. */
	@Setter
	@Column(name = "pr_number")
	private Integer prNumber;

	/** Number of uploads merged into this run. */
	@Column(nullable = false)
	private int uploads = 1;

	public TestRun(Project project, String branch, String flag, String commitSha, String buildUrl, String ciProvider) {
		this.project = project;
		this.branch = branch;
		this.flag = ((flag != null) && !flag.isBlank()) ? flag : "default";
		this.commitSha = commitSha;
		this.buildUrl = buildUrl;
		this.ciProvider = ciProvider;
	}

	public void applyTotals(int passed, int failed, int errors, int skipped, long durationMs) {
		this.passed = passed;
		this.failed = failed;
		this.errors = errors;
		this.skipped = skipped;
		this.totalTests = passed + failed + errors + skipped;
		this.durationMs = durationMs;
		this.status = ((failed + errors) == 0) ? "PASSED" : "FAILED";
	}

	/** Accumulates another upload's totals into this run (sharded/merged ingest). */
	public void addTotals(int passed, int failed, int errors, int skipped, long durationMs) {
		this.passed += passed;
		this.failed += failed;
		this.errors += errors;
		this.skipped += skipped;
		this.totalTests += passed + failed + errors + skipped;
		this.durationMs += durationMs;
		this.status = ((this.failed + this.errors) == 0) ? "PASSED" : "FAILED";
		this.uploads += 1;
	}

	public double passRate() {
		int considered = totalTests - skipped;
		return (considered == 0) ? 0.0 : (passed * 100.0) / considered;
	}

	public String getShortSha() {
		return (commitSha != null) ? commitSha.substring(0, Math.min(7, commitSha.length())) : "";
	}

}
