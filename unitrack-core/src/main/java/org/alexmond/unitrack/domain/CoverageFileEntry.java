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

/** Per-source-file coverage within a {@link CoverageReport}. */
@Entity
@Table(name = "coverage_file_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

	/**
	 * Explicit build module from the uploader (#393), or null to fall back to package
	 * derivation.
	 */
	@Column(name = "module")
	private String module;

	/**
	 * Comma-separated line numbers with no coverage, for PR annotations (#443). Populated
	 * from JaCoCo per-line data; null/empty when the format carries no line detail.
	 */
	@Column(name = "uncovered_lines", columnDefinition = "text")
	private String uncoveredLines;

	/**
	 * Repo-relative path (e.g. {@code unitrack-core/src/main/java/org/ex/Foo.java})
	 * resolved from the uploader's source manifest (#454), or null when no manifest was
	 * sent or nothing matched. Drives working GitHub source links; falls back to
	 * {@link #getPath()} (package-relative) for display.
	 */
	@Column(name = "repo_path", columnDefinition = "text")
	private String repoPath;

	public CoverageFileEntry(CoverageReport report, String packageName, String fileName, int lineCovered,
			int lineMissed, int branchCovered, int branchMissed) {
		this.report = report;
		this.packageName = packageName;
		this.fileName = fileName;
		this.lineCovered = lineCovered;
		this.lineMissed = lineMissed;
		this.branchCovered = branchCovered;
		this.branchMissed = branchMissed;
	}

	public void setModule(String module) {
		this.module = module;
	}

	public String getUncoveredLines() {
		return this.uncoveredLines;
	}

	public void setUncoveredLines(String uncoveredLines) {
		this.uncoveredLines = uncoveredLines;
	}

	public String getRepoPath() {
		return this.repoPath;
	}

	public void setRepoPath(String repoPath) {
		this.repoPath = repoPath;
	}

	/**
	 * Repo-relative path when resolved from a manifest, else the package-relative path.
	 */
	public String getLinkPath() {
		return (this.repoPath != null && !this.repoPath.isBlank()) ? this.repoPath : getPath();
	}

	public double getLinePct() {
		return CoverageReport.pct(lineCovered, lineMissed);
	}

	public String getPath() {
		return (packageName == null || packageName.isBlank()) ? fileName : packageName + "/" + fileName;
	}

}
