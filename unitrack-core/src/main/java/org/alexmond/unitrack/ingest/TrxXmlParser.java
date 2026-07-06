package org.alexmond.unitrack.ingest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLStreamReader;

import org.alexmond.unitrack.domain.TestStatus;
import org.springframework.stereotype.Component;

/**
 * Parses Visual Studio / {@code dotnet test} <strong>TRX</strong> result files (MSTest
 * XML) so .NET projects upload native test results without a trx2junit step.
 *
 * <p>
 * {@code <TestRun>} →
 * {@code <Results><UnitTestResult outcome=".." duration="hh:mm:ss.fff">} carries the
 * per-test outcome/timing (+ {@code <Output><ErrorInfo>} message/stacktrace);
 * {@code <TestDefinitions><UnitTest><TestMethod className=".." name=".."/>} maps each
 * result to its class. The two blocks are siblings in any order, so both are streamed
 * (StAX) in one pass and joined afterwards; results are grouped into {@link ParsedSuite}s
 * by class name.
 */
@Component
public class TrxXmlParser implements TestResultParser {

	@Override
	public String format() {
		return "trx";
	}

	@Override
	public boolean supports(String headSample) {
		return headSample.contains("<TestRun")
				&& (headSample.contains("VisualStudio/TeamTest") || headSample.contains("UnitTestResult"));
	}

	@Override
	public JUnitResults parse(InputStream in) {
		try {
			Map<String, String[]> defsById = new HashMap<>();
			List<XmlNode> results = new ArrayList<>();
			XMLStreamReader reader = StaxXml.open(in);
			StaxXml.forEachSubtree(reader, Set.of("UnitTestResult", "UnitTest"), (node) -> {
				if ("UnitTest".equals(node.name())) {
					addDefinition(node, defsById);
				}
				else {
					results.add(node);
				}
			});
			reader.close();

			Map<String, List<ParsedCase>> bySuite = new LinkedHashMap<>();
			for (XmlNode result : results) {
				ParsedCase parsed = toCase(result, defsById);
				bySuite.computeIfAbsent(parsed.className(), (k) -> new ArrayList<>()).add(parsed);
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
		catch (Exception ex) {
			throw new IngestException("Failed to parse TRX XML: " + ex.getMessage(), ex);
		}
	}

	/** Adds {@code testId -> [className, methodName]} for a TestDefinitions UnitTest. */
	private static void addDefinition(XmlNode unitTest, Map<String, String[]> map) {
		String id = unitTest.attr("id");
		List<XmlNode> methods = unitTest.descendants("TestMethod");
		if (!id.isEmpty() && !methods.isEmpty()) {
			XmlNode m = methods.getFirst();
			map.put(id, new String[] { className(m.attr("className")), m.attr("name") });
		}
	}

	private static ParsedCase toCase(XmlNode result, Map<String, String[]> defsById) {
		String[] def = defsById.get(result.attr("testId"));
		String className = (def != null && !def[0].isEmpty()) ? def[0] : "(unknown)";
		String name = (def != null && !def[1].isEmpty()) ? def[1] : result.attr("testName");
		TestStatus status = mapOutcome(result.attr("outcome"));
		long durationMs = parseDuration(result.attr("duration"));

		String message = null;
		String stacktrace = null;
		if (status == TestStatus.FAILED || status == TestStatus.ERROR) {
			message = result.firstDescendantText("Message");
			stacktrace = result.firstDescendantText("StackTrace");
		}
		return new ParsedCase(className, className, name, status, durationMs, null, message, stacktrace, null, null,
				List.of());
	}

	private static ParsedSuite toSuite(String name, List<ParsedCase> cases) {
		int failures = (int) cases.stream().filter((c) -> c.status() == TestStatus.FAILED).count();
		int errors = (int) cases.stream().filter((c) -> c.status() == TestStatus.ERROR).count();
		int skipped = (int) cases.stream().filter((c) -> c.status() == TestStatus.SKIPPED).count();
		long durationMs = cases.stream().mapToLong(ParsedCase::durationMs).sum();
		return new ParsedSuite(name, cases.size(), failures, errors, skipped, durationMs, cases);
	}

	/** {@code "Ns.Class, Assembly"} → {@code "Ns.Class"}. */
	private static String className(String raw) {
		if (raw == null) {
			return "";
		}
		int comma = raw.indexOf(',');
		return ((comma >= 0) ? raw.substring(0, comma) : raw).strip();
	}

	private static TestStatus mapOutcome(String outcome) {
		return switch (outcome.toLowerCase(Locale.ROOT)) {
			case "passed" -> TestStatus.PASSED;
			case "failed", "timeout", "aborted" -> TestStatus.FAILED;
			case "notexecuted", "inconclusive", "pending", "disconnected", "warning" -> TestStatus.SKIPPED;
			default -> TestStatus.ERROR;
		};
	}

	/** TRX duration is {@code hh:mm:ss[.fffffff]}. */
	private static long parseDuration(String duration) {
		if (duration == null || duration.isBlank()) {
			return 0L;
		}
		try {
			String[] parts = duration.strip().split(":");
			if (parts.length != 3) {
				return 0L;
			}
			long hours = Long.parseLong(parts[0]);
			long minutes = Long.parseLong(parts[1]);
			double seconds = Double.parseDouble(parts[2]);
			return Math.round(((hours * 60 + minutes) * 60 + seconds) * 1000.0);
		}
		catch (NumberFormatException ex) {
			return 0L;
		}
	}

}
