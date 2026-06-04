package io.github.alexmond.unitrack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Aggregated JaCoCo coverage counters for a run. */
@Entity
@Table(name = "coverage_report")
public class CoverageReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false, unique = true)
    private TestRun run;

    @Column(name = "line_covered")
    private int lineCovered;
    @Column(name = "line_missed")
    private int lineMissed;

    @Column(name = "branch_covered")
    private int branchCovered;
    @Column(name = "branch_missed")
    private int branchMissed;

    @Column(name = "instruction_covered")
    private int instructionCovered;
    @Column(name = "instruction_missed")
    private int instructionMissed;

    @Column(name = "method_covered")
    private int methodCovered;
    @Column(name = "method_missed")
    private int methodMissed;

    protected CoverageReport() {
    }

    public CoverageReport(TestRun run) {
        this.run = run;
    }

    public void setCounters(int lineCovered, int lineMissed, int branchCovered, int branchMissed,
                            int instructionCovered, int instructionMissed,
                            int methodCovered, int methodMissed) {
        this.lineCovered = lineCovered;
        this.lineMissed = lineMissed;
        this.branchCovered = branchCovered;
        this.branchMissed = branchMissed;
        this.instructionCovered = instructionCovered;
        this.instructionMissed = instructionMissed;
        this.methodCovered = methodCovered;
        this.methodMissed = methodMissed;
    }

    public static double pct(int covered, int missed) {
        int total = covered + missed;
        return total == 0 ? 0.0 : (covered * 100.0) / total;
    }

    public double getLinePct() {
        return pct(lineCovered, lineMissed);
    }

    public double getBranchPct() {
        return pct(branchCovered, branchMissed);
    }

    public double getInstructionPct() {
        return pct(instructionCovered, instructionMissed);
    }

    public double getMethodPct() {
        return pct(methodCovered, methodMissed);
    }

    public Long getId() {
        return id;
    }

    public int getLineCovered() {
        return lineCovered;
    }

    public int getLineMissed() {
        return lineMissed;
    }

    public int getBranchCovered() {
        return branchCovered;
    }

    public int getBranchMissed() {
        return branchMissed;
    }

    public int getInstructionCovered() {
        return instructionCovered;
    }

    public int getInstructionMissed() {
        return instructionMissed;
    }

    public int getMethodCovered() {
        return methodCovered;
    }

    public int getMethodMissed() {
        return methodMissed;
    }
}
