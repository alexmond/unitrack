package org.alexmond.unitrack.ingest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.alexmond.unitrack.domain.TestStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses {@code go test -json} output — a newline-delimited stream of action events
 * ({@code {"Action":"run|pass|fail|skip|output","Package":"..","Test":"..","Elapsed":..}}).
 * Test-scoped events are aggregated per {@code Package}/{@code Test} into pass/fail/skip
 * + duration, with {@code output} captured for failures. Each Go package becomes a
 * {@link ParsedSuite}.
 */
@Component
public class GoTestJsonParser implements TestResultParser {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	@Override
	public String format() {
		return "go-test";
	}

	@Override
	public boolean supports(String headSample) {
		return headSample.contains("\"Action\"") && headSample.contains("\"Time\"")
				&& headSample.contains("\"Package\"");
	}

	@Override
	public JUnitResults parse(InputStream in) {
		// pkg -> (testName -> accumulator), insertion-ordered for stable output.
		Map<String, Map<String, Acc>> byPkg = new LinkedHashMap<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				JsonNode event = MAPPER.readTree(line);
				String action = event.path("Action").asString("");
				String test = event.path("Test").asString("");
				if (action.isEmpty() || test.isEmpty()) {
					continue; // package-level event (or non-event line) — only tests
								// become cases
				}
				String pkg = event.path("Package").asString("(default)");
				Acc acc = byPkg.computeIfAbsent(pkg, (k) -> new LinkedHashMap<>())
					.computeIfAbsent(test, (k) -> new Acc());
				switch (action) {
					case "pass" -> acc.finish(TestStatus.PASSED, event);
					case "fail" -> acc.finish(TestStatus.FAILED, event);
					case "skip" -> acc.finish(TestStatus.SKIPPED, event);
					case "output" -> acc.output.add(event.path("Output").asString(""));
					default -> {
						// "run"/"cont"/"pause" — no terminal info
					}
				}
			}
		}
		catch (RuntimeException | java.io.IOException ex) {
			throw new IngestException("Failed to parse go test -json: " + ex.getMessage(), ex);
		}

		List<ParsedSuite> suites = new ArrayList<>();
		for (Map.Entry<String, Map<String, Acc>> pkg : byPkg.entrySet()) {
			List<ParsedCase> cases = new ArrayList<>();
			for (Map.Entry<String, Acc> t : pkg.getValue().entrySet()) {
				cases.add(t.getValue().toCase(pkg.getKey(), t.getKey()));
			}
			suites.add(toSuite(pkg.getKey(), cases));
		}
		return new JUnitResults(suites);
	}

	private static ParsedSuite toSuite(String name, List<ParsedCase> cases) {
		int failures = (int) cases.stream().filter((c) -> c.status() == TestStatus.FAILED).count();
		int skipped = (int) cases.stream().filter((c) -> c.status() == TestStatus.SKIPPED).count();
		long durationMs = cases.stream().mapToLong(ParsedCase::durationMs).sum();
		return new ParsedSuite(name, cases.size(), failures, 0, skipped, durationMs, cases);
	}

	/** Mutable per-test accumulator across the event stream. */
	private static final class Acc {

		private TestStatus status = TestStatus.PASSED;

		private long durationMs;

		private final List<String> output = new ArrayList<>();

		void finish(TestStatus s, JsonNode event) {
			this.status = s;
			this.durationMs = Math.round(event.path("Elapsed").asDouble(0.0) * 1000.0);
		}

		ParsedCase toCase(String pkg, String test) {
			String trace = null;
			if (this.status == TestStatus.FAILED && !this.output.isEmpty()) {
				trace = String.join("", this.output).strip();
			}
			// Go has no class; the package is the grouping unit.
			return new ParsedCase(pkg, pkg, test, this.status, this.durationMs, null, null, trace, null, null,
					List.of());
		}

	}

}
