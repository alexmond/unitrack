package org.alexmond.unitrack.domain;

import java.time.Instant;

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

/**
 * One performance/load-test run: aggregate latency percentiles, throughput and error
 * rate, keyed by project/branch/commit like a {@link TestRun}. Per-request-label detail
 * lives in {@link PerfTransaction}.
 */
@Entity
@Table(name = "perf_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerfRun {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Column
	private String branch;

	@Column(nullable = false)
	private String flag = "default";

	@Column(name = "commit_sha")
	private String commitSha;

	@Column(name = "build_url")
	private String buildUrl;

	@Column(name = "ci_provider")
	private String ciProvider;

	@Setter
	@Column(name = "run_key")
	private String runKey;

	@Column(nullable = false, length = 16)
	private String format;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	// --- metrics (set after parsing) ---

	@Setter
	@Column(name = "sample_count", nullable = false)
	private long sampleCount;

	@Setter
	@Column(name = "error_count", nullable = false)
	private long errorCount;

	@Setter
	@Column(name = "error_pct", nullable = false)
	private double errorPct;

	@Setter
	@Column(name = "throughput_rps", nullable = false)
	private double throughputRps;

	@Setter
	@Column(name = "duration_ms", nullable = false)
	private long durationMs;

	@Setter
	@Column(name = "mean_ms", nullable = false)
	private double meanMs;

	@Setter
	@Column(name = "p50_ms", nullable = false)
	private double p50Ms;

	@Setter
	@Column(name = "p90_ms", nullable = false)
	private double p90Ms;

	@Setter
	@Column(name = "p95_ms", nullable = false)
	private double p95Ms;

	@Setter
	@Column(name = "p99_ms", nullable = false)
	private double p99Ms;

	@Setter
	@Column(name = "min_ms", nullable = false)
	private double minMs;

	@Setter
	@Column(name = "max_ms", nullable = false)
	private double maxMs;

	public PerfRun(Project project, String branch, String flag, String commitSha, String buildUrl, String ciProvider,
			String format) {
		this.project = project;
		this.branch = branch;
		this.flag = ((flag != null) && !flag.isBlank()) ? flag : "default";
		this.commitSha = commitSha;
		this.buildUrl = buildUrl;
		this.ciProvider = ciProvider;
		this.format = format;
	}

	/** True when no samples errored. */
	public boolean isOk() {
		return this.errorCount == 0;
	}

}
