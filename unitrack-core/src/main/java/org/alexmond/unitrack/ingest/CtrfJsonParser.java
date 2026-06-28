package org.alexmond.unitrack.ingest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.alexmond.unitrack.domain.TestStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
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
		// Stream results.tests one element at a time (the array can be huge); the tool
		// name
		// (the suite fallback) is captured wherever it appears, so the suite grouping is
		// resolved after the pass and works regardless of tool/tests field order (#369).
		try (JsonParser p = MAPPER.createParser(in)) {
			String tool = "ctrf";
			boolean sawTests = false;
			List<PendingTest> pending = new ArrayList<>();
			JsonToken token;
			while ((token = p.nextToken()) != null) {
				if (token != JsonToken.PROPERTY_NAME) {
					continue;
				}
				String field = p.currentName();
				if ("tool".equals(field) && p.nextToken() == JsonToken.START_OBJECT) {
					JsonNode toolNode = p.readValueAsTree();
					String name = toolNode.path("name").asString("");
					if (!name.isBlank()) {
						tool = name;
					}
				}
				else if ("tests".equals(field) && p.nextToken() == JsonToken.START_ARRAY) {
					sawTests = true;
					while (p.nextToken() != JsonToken.END_ARRAY) {
						pending.add(toPending(p.readValueAsTree()));
					}
				}
			}
			if (!sawTests) {
				throw new IngestException("Not a CTRF report: results.tests array is missing");
			}

			// Preserve first-seen suite order for a stable result.
			Map<String, List<ParsedCase>> bySuite = new LinkedHashMap<>();
			for (PendingTest pt : pending) {
				String suite = pt.suite().isBlank() ? tool : pt.suite();
				bySuite.computeIfAbsent(suite, (k) -> new ArrayList<>()).add(toCase(suite, pt));
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

	private static PendingTest toPending(JsonNode test) {
		return new PendingTest(test.path("suite").asString(""), test.path("name").asString("(unnamed)"),
				mapStatus(test.path("status").asString("")), test.path("duration").asLong(0L),
				emptyToNull(test.path("message").asString("")), emptyToNull(test.path("trace").asString("")));
	}

	private static ParsedCase toCase(String suite, PendingTest test) {
		boolean failed = test.status() == TestStatus.FAILED || test.status() == TestStatus.ERROR;
		String failureMessage = failed ? test.message() : null;
		String failureTrace = failed ? test.trace() : null;
		// CTRF tests carry no class; use the suite as the class so the UI groups
		// sensibly.
		return new ParsedCase(suite, suite, test.name(), test.status(), test.durationMs(), null, failureMessage,
				failureTrace, null, null, List.of());
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

	/**
	 * One streamed CTRF test, with the suite fallback deferred until the tool is known.
	 */
	private record PendingTest(String suite, String name, TestStatus status, long durationMs, String message,
			String trace) {
	}

}
