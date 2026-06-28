package org.alexmond.unitrack.ingest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.alexmond.unitrack.domain.TestStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses the <a href="https://ctrf.io">CTRF</a> (Common Test Report Format) JSON schema —
 * a language-agnostic test report with reporters across many frameworks (Jest,
 * Playwright, pytest, PHPUnit, Go, …). One parser, many ecosystems.
 *
 * <p>
 * Shape: {@code {"results":{"tool":{...},"summary":{...},"tests":[{"name","status",
 * "duration","message","trace","suite"}]}}}. Tests are grouped into {@link ParsedSuite}s
 * by their {@code suite} (falling back to the tool name). CTRF {@code duration} is
 * milliseconds.
 */
@Component
public class CtrfJsonParser implements TestResultParser {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	@Override
	public String format() {
		return "ctrf";
	}

	@Override
	public boolean supports(String headSample) {
		String h = headSample.stripLeading();
		return h.startsWith("{") && headSample.contains("\"results\"") && headSample.contains("\"tests\"");
	}

	@Override
	public JUnitResults parse(InputStream in) {
		try {
			JsonNode results = MAPPER.readTree(in).path("results");
			JsonNode testsNode = results.path("tests");
			if (!testsNode.isArray()) {
				throw new IngestException("Not a CTRF report: results.tests array is missing");
			}
			String tool = results.path("tool").path("name").asString("ctrf");
			// Preserve first-seen suite order for a stable result.
			Map<String, List<ParsedCase>> bySuite = new LinkedHashMap<>();
			for (JsonNode test : testsNode) {
				String suite = test.path("suite").asString("");
				if (suite.isBlank()) {
					suite = tool;
				}
				bySuite.computeIfAbsent(suite, (k) -> new ArrayList<>()).add(toCase(suite, test));
			}
			List<ParsedSuite> suites = new ArrayList<>();
			for (Map.Entry<String, List<ParsedCase>> e : bySuite.entrySet()) {
				suites.add(toSuite(e.getKey(), e.getValue()));
			}
			return new JUnitResults(suites);
		}
		catch (IngestException ex) {
			throw ex;
		}
		catch (RuntimeException ex) {
			throw new IngestException("Failed to parse CTRF JSON: " + ex.getMessage(), ex);
		}
	}

	private static ParsedCase toCase(String suite, JsonNode test) {
		String name = test.path("name").asString("(unnamed)");
		TestStatus status = mapStatus(test.path("status").asString(""));
		long durationMs = test.path("duration").asLong(0L);
		String message = emptyToNull(test.path("message").asString(""));
		String trace = emptyToNull(test.path("trace").asString(""));
		String failureMessage = (status == TestStatus.FAILED || status == TestStatus.ERROR) ? message : null;
		String failureTrace = (status == TestStatus.FAILED || status == TestStatus.ERROR) ? trace : null;
		// CTRF tests carry no class; use the suite as the class so the UI groups
		// sensibly.
		return new ParsedCase(suite, suite, name, status, durationMs, null, failureMessage, failureTrace, null, null,
				List.of());
	}

	private static ParsedSuite toSuite(String name, List<ParsedCase> cases) {
		int failures = (int) cases.stream().filter((c) -> c.status() == TestStatus.FAILED).count();
		int errors = (int) cases.stream().filter((c) -> c.status() == TestStatus.ERROR).count();
		int skipped = (int) cases.stream().filter((c) -> c.status() == TestStatus.SKIPPED).count();
		long durationMs = cases.stream().mapToLong(ParsedCase::durationMs).sum();
		return new ParsedSuite(name, cases.size(), failures, errors, skipped, durationMs, cases);
	}

	private static TestStatus mapStatus(String status) {
		return switch (status.toLowerCase(java.util.Locale.ROOT)) {
			case "passed" -> TestStatus.PASSED;
			case "failed" -> TestStatus.FAILED;
			case "skipped", "pending" -> TestStatus.SKIPPED;
			default -> TestStatus.ERROR;
		};
	}

	private static String emptyToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
