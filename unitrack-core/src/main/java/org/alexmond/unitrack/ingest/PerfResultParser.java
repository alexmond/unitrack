package org.alexmond.unitrack.ingest;

import java.io.InputStream;

/**
 * Parses one performance-test result format into the common {@link PerfResults}.
 * Implementations self-identify via {@link #supports(String)} so uploads can be
 * auto-detected by content (mirrors {@link CoverageParser}).
 */
public interface PerfResultParser {

	/** Short format id (e.g. {@code jmeter}, {@code k6}). */
	String format();

	/** Whether this parser recognises the report from a sample of its head. */
	boolean supports(String headSample);

	/** Parses the report. Throws {@link IngestException} on malformed input. */
	PerfResults parse(InputStream in);

}
