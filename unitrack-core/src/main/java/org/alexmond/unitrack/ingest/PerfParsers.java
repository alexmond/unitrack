package org.alexmond.unitrack.ingest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Auto-detects a performance-result upload's format from its content and dispatches to
 * the matching {@link PerfResultParser} (mirrors {@link CoverageParsers}).
 */
@Component
@RequiredArgsConstructor
public class PerfParsers {

	private static final int HEAD_SAMPLE_BYTES = 8192;

	private final List<PerfResultParser> parsers;

	public Parsed parse(InputStream in) {
		byte[] content;
		try {
			content = in.readAllBytes();
		}
		catch (Exception ex) {
			throw new IngestException("Failed reading performance upload: " + ex.getMessage(), ex);
		}
		int sampleLen = Math.min(content.length, HEAD_SAMPLE_BYTES);
		String head = new String(content, 0, sampleLen, StandardCharsets.UTF_8);
		for (PerfResultParser parser : this.parsers) {
			if (parser.supports(head)) {
				return new Parsed(parser.format(), parser.parse(new ByteArrayInputStream(content)));
			}
		}
		throw new IngestException("Unrecognized performance result format (expected JMeter JTL or k6 JSON summary)");
	}

	/** The detected format id plus the parsed results. */
	public record Parsed(String format, PerfResults results) {
	}

}
