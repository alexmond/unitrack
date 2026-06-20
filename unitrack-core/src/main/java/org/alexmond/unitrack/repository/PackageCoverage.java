package org.alexmond.unitrack.repository;

/**
 * Projection: line/branch coverage totals aggregated for one package of a coverage
 * report.
 */
public interface PackageCoverage {

	String getPackageName();

	long getLineCovered();

	long getLineMissed();

	long getBranchCovered();

	long getBranchMissed();

	long getFiles();

}
