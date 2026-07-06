package org.alexmond.unitrack.ingest;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Auto-detects a test-result upload's format from its content and dispatches to the
 * matching {@link TestResultParser} (mirrors {@link PerfParsers} /
 * {@link CoverageParsers}).
 */
@Component
@RequiredArgsConstructor
public class TestResultParsers {

	private static final int HEAD_SAMPLE_BYTES = 8192;

	private final List<TestResultParser> parsers;

	public Parsed parse(InputStream in) {
		try {
			BufferedInputStream bis = StreamSniff.buffered(in, HEAD_SAMPLE_BYTES);
			String head = StreamSniff.head(bis, HEAD_SAMPLE_BYTES);
			for (TestResultParser parser : this.parsers) {
				if (parser.supports(head)) {
					return new Parsed(parser.format(), parser.parse(bis));
				}
			}
		}
		catch (IOException ex) {
			throw new IngestException("Failed reading test-result upload: " + ex.getMessage(), ex);
		}
		throw new IngestException("Unrecognized test-result format (expected JUnit/Surefire XML, .NET TRX, "
				+ "xUnit/NUnit XML, CTRF JSON or Go test -json)");
	}

	/** The detected format id plus the parsed results. */
	public record Parsed(String format, JUnitResults results) {
	}

}
