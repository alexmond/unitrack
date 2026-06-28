package org.alexmond.unitrack.ingest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLStreamReader;

import org.alexmond.unitrack.domain.TestStatus;
import org.springframework.stereotype.Component;

/**
 * Parses NUnit 3 XML ({@code <test-run>}). Each
 * {@code <test-case name=".." classname=".."
 * methodname=".." result="Passed|Failed|Skipped|Inconclusive" duration="0.12">} (with a
 * nested {@code <failure><message/><stack-trace/></failure>}) becomes a case, grouped
 * into {@link ParsedSuite}s by {@code classname}.
 */
@Component
public class NUnitXmlParser implements TestResultParser {

	@Override
	public String format() {
		return "nunit";
	}

	@Override
	public boolean supports(String headSample) {
		return headSample.contains("<test-run");
	}

	@Override
	public JUnitResults parse(InputStream in) {
		try {
			Map<String, List<ParsedCase>> bySuite = new LinkedHashMap<>();
			XMLStreamReader reader = StaxXml.open(in);
			StaxXml.forEachSubtree(reader, Set.of("test-case"), (test) -> {
				ParsedCase parsed = toCase(test);
				bySuite.computeIfAbsent(parsed.className(), (k) -> new ArrayList<>()).add(parsed);
			});
			reader.close();
			List<ParsedSuite> suites = new ArrayList<>();
			for (Map.Entry<String, List<ParsedCase>> e : bySuite.entrySet()) {
				suites.add(Suites.of(e.getKey(), e.getValue()));
			}
			return new JUnitResults(suites);
		}
		catch (Exception ex) {
			throw new IngestException("Failed to parse NUnit XML: " + ex.getMessage(), ex);
		}
	}

	private static ParsedCase toCase(XmlNode test) {
		String classname = test.attr("classname");
		String className = !classname.isEmpty() ? classname : deriveClass(test.attr("fullname"));
		if (className.isEmpty()) {
			className = "(unknown)";
		}
		String methodname = test.attr("methodname");
		String name = !methodname.isEmpty() ? methodname : test.attr("name");
		TestStatus status = mapResult(test.attr("result"));
		long durationMs = test.attrSecondsToMillis("duration");

		String message = null;
		String stacktrace = null;
		if (status == TestStatus.FAILED || status == TestStatus.ERROR) {
			message = test.firstDescendantText("message");
			stacktrace = test.firstDescendantText("stack-trace");
		}
		return new ParsedCase(className, className, name, status, durationMs, null, message, stacktrace, null, null,
				List.of());
	}

	/** {@code "NS.Class.Method"} → {@code "NS.Class"} when classname isn't set. */
	private static String deriveClass(String fullname) {
		int dot = fullname.lastIndexOf('.');
		return (dot > 0) ? fullname.substring(0, dot) : fullname;
	}

	private static TestStatus mapResult(String result) {
		return switch (result.toLowerCase(Locale.ROOT)) {
			case "passed" -> TestStatus.PASSED;
			case "failed" -> TestStatus.FAILED;
			case "skipped", "inconclusive", "warning" -> TestStatus.SKIPPED;
			default -> TestStatus.ERROR;
		};
	}

}
