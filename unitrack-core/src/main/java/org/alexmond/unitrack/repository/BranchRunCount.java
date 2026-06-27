package org.alexmond.unitrack.repository;

/** Projection: total run count for one branch of a project (batched branches list). */
public interface BranchRunCount {

	String getBranch();

	long getCnt();

}
