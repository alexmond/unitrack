package org.alexmond.unitrack.domain;

/** User-controlled lifecycle state for a detected flaky test. */
public enum FlakyStatus {

	/** Detected as flaky and counted against the build. */
	ACTIVE,

	/** Known flaky and muted — its failures should not fail the build. */
	QUARANTINED,

	/** Marked fixed; kept for history until it flakes again. */
	RESOLVED

}
