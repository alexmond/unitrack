package org.alexmond.unitrack.repository;

/** Projection: number of flaky tests for one project (board batch query). */
public interface ProjectFlakyCount {

	Long getProjectId();

	long getCnt();

}
