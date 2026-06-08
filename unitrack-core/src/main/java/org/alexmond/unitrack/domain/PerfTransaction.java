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

/**
 * Per-request-label metrics within a {@link PerfRun} — the "which endpoint regressed"
 * view.
 */
@Entity
@Table(name = "perf_transaction")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerfTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "perf_run_id", nullable = false)
	private PerfRun perfRun;

	@Column(nullable = false, length = 512)
	private String label;

	@Column(name = "sample_count", nullable = false)
	private long sampleCount;

	@Column(name = "error_count", nullable = false)
	private long errorCount;

	@Column(name = "error_pct", nullable = false)
	private double errorPct;

	@Column(name = "mean_ms", nullable = false)
	private double meanMs;

	@Column(name = "p50_ms", nullable = false)
	private double p50Ms;

	@Column(name = "p90_ms", nullable = false)
	private double p90Ms;

	@Column(name = "p95_ms", nullable = false)
	private double p95Ms;

	@Column(name = "p99_ms", nullable = false)
	private double p99Ms;

	public PerfTransaction(PerfRun perfRun, String label) {
		this.perfRun = perfRun;
		this.label = label;
	}

}
