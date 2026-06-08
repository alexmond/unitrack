package org.alexmond.unitrack.domain;

/**
 * A user's role on a single project. OWNER can manage members and write; WRITE can write
 * (settings, triage, flaky); READ can view. Global {@link Role#ADMIN} users implicitly
 * have OWNER on every project.
 */
public enum ProjectRole {

	OWNER, WRITE, READ

}
