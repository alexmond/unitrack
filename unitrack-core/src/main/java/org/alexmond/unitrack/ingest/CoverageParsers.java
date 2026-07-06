package org.alexmond.unitrack.ingest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

import edu.hm.hafner.coverage.CoverageParser.ProcessingMode;
import edu.hm.hafner.coverage.parser.CoberturaParser;
import edu.hm.hafner.coverage.parser.JacocoParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Auto-detects a coverage upload's format from its content and parses it. Parsing goes
 * through the maintained coverage-model library (the Jenkins Coverage engine); the
 * hand-rolled {@link CoverageParser}s are kept as a lenient fallback for reports
 * coverage-model rejects (it is stricter / standards-conformant), so a slightly-off
 * report from any CI producer still ingests.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CoverageParsers {

	private static final int HEAD_SAMPLE_BYTES = 8192;

	private final List<CoverageParser> parsers;

	/** Reads the report, detects its format, and parses it (coverage-model first). */
	public CoverageResults parse(InputStream in) {
		byte[] content;
		try {
			content = in.readAllBytes();
		}
		catch (IOException ex) {
			throw new IngestException("Failed reading coverage upload: " + ex.getMessage(), ex);
		}
		int sampleLen = Math.min(content.length, HEAD_SAMPLE_BYTES);
		String head = new String(content, 0, sampleLen, StandardCharsets.UTF_8);

		CoverageParser fallback = null;
		for (CoverageParser parser : this.parsers) {
			if (parser.supports(head)) {
				fallback = parser;
				break;
			}
		}
		if (fallback == null) {
			throw new IngestException(
					"Unrecognized coverage format (expected JaCoCo, Cobertura, LCOV, OpenCover, or Go cover)");
		}

		Supplier<edu.hm.hafner.coverage.CoverageParser> model = coverageModelParser(fallback.format());
		if (model != null) {
			try {
				return CoverageModelAdapter.parse(model.get(), new ByteArrayInputStream(content), fallback.format());
			}
			catch (IngestException ex) {
				log.warn("coverage-model could not parse {} ({}); falling back to the lenient parser",
						fallback.format(), ex.getMessage());
			}
		}
		return fallback.parse(new ByteArrayInputStream(content));
	}

	/**
	 * The coverage-model parser for a detected format, or null to use the hand-rolled
	 * parser. JaCoCo + Cobertura are validated faithful (line/branch match). LCOV stays
	 * hand-rolled — coverage-model under-counts LCOV branch coverage; OpenCover stays
	 * hand-rolled — its coverage-model parser requires {@code cyclomaticComplexity} that
	 * some producers omit; Go cover has no coverage-model parser. Tracked in #339.
	 */
	private static Supplier<edu.hm.hafner.coverage.CoverageParser> coverageModelParser(String format) {
		return switch (format) {
			case "jacoco" -> () -> new JacocoParser(ProcessingMode.FAIL_FAST);
			case "cobertura" -> () -> new CoberturaParser(ProcessingMode.FAIL_FAST);
			default -> null;
		};
	}

}
