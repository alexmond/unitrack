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

/** Per-source-file coverage within a {@link CoverageReport}. */
@Entity
@Table(name = "coverage_file_entry")
public class CoverageFileEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private CoverageReport report;

    @Column(name = "package_name")
    private String packageName;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "line_covered")
    private int lineCovered;
    @Column(name = "line_missed")
    private int lineMissed;
    @Column(name = "branch_covered")
    private int branchCovered;
    @Column(name = "branch_missed")
    private int branchMissed;

    protected CoverageFileEntry() {
    }

    public CoverageFileEntry(CoverageReport report, String packageName, String fileName,
                             int lineCovered, int lineMissed, int branchCovered, int branchMissed) {
        this.report = report;
        this.packageName = packageName;
        this.fileName = fileName;
        this.lineCovered = lineCovered;
        this.lineMissed = lineMissed;
        this.branchCovered = branchCovered;
        this.branchMissed = branchMissed;
    }

    public double getLinePct() {
        return CoverageReport.pct(lineCovered, lineMissed);
    }

    public Long getId() {
        return id;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getFileName() {
        return fileName;
    }

    public String getPath() {
        return (packageName == null || packageName.isBlank()) ? fileName : packageName + "/" + fileName;
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
}
