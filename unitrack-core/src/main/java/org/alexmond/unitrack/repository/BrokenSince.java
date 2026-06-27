package org.alexmond.unitrack.repository;

import java.time.Instant;

/**
 * Projection: for a project whose latest run is failing, how long it has been red. The
 * streak is scoped to the latest run's branch+flag; {@code lastGreenAt} is null when the
 * series has never passed. {@code runsRed} counts the runs shipped on top of the breakage
 * (exposure).
 */
public interface BrokenSince {

	Long getProjectId();

	Instant getLastGreenAt();

	Instant getBrokenSince();

	long getRunsRed();

}
