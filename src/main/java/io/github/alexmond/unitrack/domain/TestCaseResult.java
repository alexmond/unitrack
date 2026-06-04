package io.github.alexmond.unitrack.domain;

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

/** A single &lt;testcase&gt; outcome within a run. */
@Entity
@Table(name = "test_case_result")
public class TestCaseResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private TestRun run;

    @Column(name = "suite_name")
    private String suiteName;

    @Column(name = "class_name")
    private String className;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestStatus status;

    @Column(name = "duration_ms")
    private long durationMs;

    @Column(name = "failure_type")
    private String failureType;

    @Column(name = "failure_message", length = 2000)
    private String failureMessage;

    @Column(name = "failure_stacktrace", length = 100_000)
    private String failureStacktrace;

    protected TestCaseResult() {
    }

    public TestCaseResult(TestRun run, String suiteName, String className, String name,
                          TestStatus status, long durationMs) {
        this.run = run;
        this.suiteName = suiteName;
        this.className = className;
        this.name = name;
        this.status = status;
        this.durationMs = durationMs;
    }

    public void setFailure(String failureType, String failureMessage, String failureStacktrace) {
        this.failureType = failureType;
        this.failureMessage = truncate(failureMessage);
        this.failureStacktrace = failureStacktrace;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    public Long getId() {
        return id;
    }

    public String getSuiteName() {
        return suiteName;
    }

    public String getClassName() {
        return className;
    }

    public String getName() {
        return name;
    }

    public TestStatus getStatus() {
        return status;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getFailureType() {
        return failureType;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public String getFailureStacktrace() {
        return failureStacktrace;
    }
}
