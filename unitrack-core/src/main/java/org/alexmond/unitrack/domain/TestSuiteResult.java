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

/** Aggregated results for one &lt;testsuite&gt; within a run. */
@Entity
@Table(name = "test_suite_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TestSuiteResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "run_id", nullable = false)
	private TestRun run;

	@Column(nullable = false)
	private String name;

	private int tests;

	private int failures;

	private int errors;

	private int skipped;

	@Column(name = "duration_ms")
	private long durationMs;

	/**
	 * Explicit build module from the uploader (#393), or null to fall back to package
	 * derivation.
	 */
	@Column(name = "module")
	private String module;

	public TestSuiteResult(TestRun run, String name, int tests, int failures, int errors, int skipped,
			long durationMs) {
		this.run = run;
		this.name = name;
		this.tests = tests;
		this.failures = failures;
		this.errors = errors;
		this.skipped = skipped;
		this.durationMs = durationMs;
	}

	public void setModule(String module) {
		this.module = module;
	}

}
