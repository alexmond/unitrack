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

import java.time.Instant;

/** One ingestion of test results (and optionally coverage) for a project at a commit. */
@Entity
@Table(name = "test_run")
public class TestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    private String branch;

    @Column(name = "commit_sha")
    private String commitSha;

    @Column(name = "build_url")
    private String buildUrl;

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
    @Column(name = "line_coverage_pct")
    private Double lineCoveragePct;

    @Column(name = "branch_coverage_pct")
    private Double branchCoveragePct;

    protected TestRun() {
    }

    public TestRun(Project project, String branch, String commitSha, String buildUrl, String ciProvider) {
        this.project = project;
        this.branch = branch;
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
        this.status = (failed + errors) == 0 ? "PASSED" : "FAILED";
    }

    public double passRate() {
        int considered = totalTests - skipped;
        return considered == 0 ? 0.0 : (passed * 100.0) / considered;
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getBranch() {
        return branch;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public String getShortSha() {
        return commitSha == null ? "" : commitSha.substring(0, Math.min(7, commitSha.length()));
    }

    public String getBuildUrl() {
        return buildUrl;
    }

    public String getCiProvider() {
        return ciProvider;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getTotalTests() {
        return totalTests;
    }

    public int getPassed() {
        return passed;
    }

    public int getFailed() {
        return failed;
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

    public String getStatus() {
        return status;
    }

    public Double getLineCoveragePct() {
        return lineCoveragePct;
    }

    public void setLineCoveragePct(Double lineCoveragePct) {
        this.lineCoveragePct = lineCoveragePct;
    }

    public Double getBranchCoveragePct() {
        return branchCoveragePct;
    }

    public void setBranchCoveragePct(Double branchCoveragePct) {
        this.branchCoveragePct = branchCoveragePct;
    }
}
