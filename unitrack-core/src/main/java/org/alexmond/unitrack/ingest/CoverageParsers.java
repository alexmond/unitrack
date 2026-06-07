package org.alexmond.unitrack.ingest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Auto-detects a coverage upload's format from its content and dispatches to the matching
 * {@link CoverageParser} (JaCoCo XML, Cobertura XML, or LCOV).
 */
@Component
@RequiredArgsConstructor
public class CoverageParsers {

	private static final int HEAD_SAMPLE_BYTES = 8192;

	private final List<CoverageParser> parsers;

	/** Reads the report, detects its format, and parses it. */
	public CoverageResults parse(InputStream in) {
		byte[] content;
		try {
			content = in.readAllBytes();
		}
		catch (Exception ex) {
			throw new IngestException("Failed reading coverage upload: " + ex.getMessage(), ex);
		}
		int sampleLen = Math.min(content.length, HEAD_SAMPLE_BYTES);
		String head = new String(content, 0, sampleLen, StandardCharsets.UTF_8);
		for (CoverageParser parser : this.parsers) {
			if (parser.supports(head)) {
				return parser.parse(new ByteArrayInputStream(content));
			}
		}
		throw new IngestException("Unrecognized coverage format (expected JaCoCo, Cobertura, or LCOV)");
	}

}
