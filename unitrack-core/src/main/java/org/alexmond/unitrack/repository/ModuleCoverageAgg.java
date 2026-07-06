package org.alexmond.unitrack.repository;

/**
 * Projection: line/branch coverage totals aggregated for one explicit module (#393) of a
 * coverage report. {@code module} is null for files an uploader didn't tag.
 */
public interface ModuleCoverageAgg {

	String getModule();

	long getLineCovered();

	long getLineMissed();

	long getBranchCovered();

	long getBranchMissed();

	long getFiles();

}
