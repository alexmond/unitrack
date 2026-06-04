package io.github.alexmond.unitrack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Aggregated results for one &lt;testsuite&gt; within a run. */
@Entity
@Table(name = "test_suite_result")
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

    protected TestSuiteResult() {
    }

    public TestSuiteResult(TestRun run, String name, int tests, int failures, int errors, int skipped, long durationMs) {
        this.run = run;
        this.name = name;
        this.tests = tests;
        this.failures = failures;
        this.errors = errors;
        this.skipped = skipped;
        this.durationMs = durationMs;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getTests() {
        return tests;
    }

    public int getFailures() {
        return failures;
    }

    public int getErrors() {
        return errors;
    }

    public int getSkipped() {
        return skipped;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
