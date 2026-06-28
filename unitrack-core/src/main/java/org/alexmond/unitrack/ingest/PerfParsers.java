package org.alexmond.unitrack.ingest;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
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
		// Peek the head for detection, then hand the SAME stream to the parser so it
		// streams
		// the rest — never buffer the whole upload (a multi-hour JTL would OOM).
		try {
			BufferedInputStream bis = StreamSniff.buffered(in, HEAD_SAMPLE_BYTES);
			String head = StreamSniff.head(bis, HEAD_SAMPLE_BYTES);
			for (PerfResultParser parser : this.parsers) {
				if (parser.supports(head)) {
					return new Parsed(parser.format(), parser.parse(bis));
				}
			}
		}
		catch (IOException ex) {
			throw new IngestException("Failed reading performance upload: " + ex.getMessage(), ex);
		}
		throw new IngestException(
				"Unrecognized performance result format (expected JMeter JTL, k6 JSON, JMH JSON or Gatling)");
	}

	/** The detected format id plus the parsed results. */
	public record Parsed(String format, PerfResults results) {
	}

}
