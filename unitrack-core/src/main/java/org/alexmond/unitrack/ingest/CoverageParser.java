package org.alexmond.unitrack.ingest;

import java.io.InputStream;

/**
 * Parses one coverage report format into the common {@link CoverageResults}.
 * Implementations self-identify via {@link #supports(String)} so uploads can be
 * auto-detected by content.
 */
public interface CoverageParser {

	/** Short format id (e.g. {@code jacoco}, {@code cobertura}, {@code lcov}). */
	String format();

	/** Whether this parser recognises the report from a sample of its head. */
	boolean supports(String headSample);

	/** Parses the report. Throws {@link IngestException} on malformed input. */
	CoverageResults parse(InputStream in);

}
