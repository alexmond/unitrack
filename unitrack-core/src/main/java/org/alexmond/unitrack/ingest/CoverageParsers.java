package org.alexmond.unitrack.ingest;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Auto-detects a coverage upload's format from its content and dispatches to the matching
 * {@link CoverageParser} (JaCoCo XML, Cobertura XML, LCOV, or OpenCover XML).
 */
@Component
@RequiredArgsConstructor
public class CoverageParsers {

	private static final int HEAD_SAMPLE_BYTES = 8192;

	private final List<CoverageParser> parsers;

	/** Reads the report, detects its format, and parses it. */
	public CoverageResults parse(InputStream in) {
		try {
			BufferedInputStream bis = StreamSniff.buffered(in, HEAD_SAMPLE_BYTES);
			String head = StreamSniff.head(bis, HEAD_SAMPLE_BYTES);
			for (CoverageParser parser : this.parsers) {
				if (parser.supports(head)) {
					return parser.parse(bis);
				}
			}
		}
		catch (IOException ex) {
			throw new IngestException("Failed reading coverage upload: " + ex.getMessage(), ex);
		}
		throw new IngestException(
				"Unrecognized coverage format (expected JaCoCo, Cobertura, LCOV, OpenCover, or Go cover)");
	}

}
