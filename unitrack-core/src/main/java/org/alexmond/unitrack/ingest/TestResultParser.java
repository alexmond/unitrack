package org.alexmond.unitrack.ingest;

import java.io.InputStream;

/**
 * Parses one test-result format into the common {@link JUnitResults} model.
 * Implementations self-identify via {@link #supports(String)} so an upload's format can
 * be auto-detected by content (mirrors {@link PerfResultParser} /
 * {@link CoverageParser}). This is how UniTrack stays polyglot — JUnit/Surefire XML, .NET
 * TRX, CTRF JSON, Go {@code test -json}, etc. all land in one model.
 */
public interface TestResultParser {

	/**
	 * Short format id (e.g. {@code junit}, {@code trx}, {@code ctrf}, {@code go-test}).
	 */
	String format();

	/** Whether this parser recognises the report from a sample of its head. */
	boolean supports(String headSample);

	/** Parses the report. Throws {@link IngestException} on malformed input. */
	JUnitResults parse(InputStream in);

}
