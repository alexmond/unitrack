package org.alexmond.unitrack.ingest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.alexmond.unitrack.domain.TestStatus;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

/**
 * Parses xUnit.net v2 XML (`dotnet test --logger xunit` / {@code -xml}). Shape:
 * {@code <assemblies><assembly><collection><test name=".." type=".." method=".."
 * time="0.12" result=
"Pass|Fail|Skip"><failure><message/><stack-trace/></failure></test>}. Tests are grouped
 * into {@link ParsedSuite}s by their {@code type} (the test class).
 */
@Component
public class XUnitXmlParser implements TestResultParser {

	@Override
	public String format() {
		return "xunit";
	}

	@Override
	public boolean supports(String headSample) {
		return headSample.contains("<assemblies") || (headSample.contains("<assembly") && headSample.contains("xUnit"));
	}

	@Override
	public JUnitResults parse(InputStream in) {
		try {
			Element root = XmlSupport.parse(in).getDocumentElement();
			Map<String, List<ParsedCase>> bySuite = new LinkedHashMap<>();
			for (Element test : XmlSupport.descendants(root, "test")) {
				ParsedCase parsed = toCase(test);
				bySuite.computeIfAbsent(parsed.className(), (k) -> new ArrayList<>()).add(parsed);
			}
			List<ParsedSuite> suites = new ArrayList<>();
			for (Map.Entry<String, List<ParsedCase>> e : bySuite.entrySet()) {
				suites.add(Suites.of(e.getKey(), e.getValue()));
			}
			return new JUnitResults(suites);
		}
		catch (Exception ex) {
			throw new IngestException("Failed to parse xUnit.net XML: " + ex.getMessage(), ex);
		}
	}

	private static ParsedCase toCase(Element test) {
		String type = test.getAttribute("type");
		String className = type.isEmpty() ? "(unknown)" : type;
		String method = test.getAttribute("method");
		String name = !method.isEmpty() ? method : test.getAttribute("name");
		TestStatus status = mapResult(test.getAttribute("result"));
		long durationMs = Suites.secondsToMillis(test.getAttribute("time"));

		String message = null;
		String stacktrace = null;
		if (status == TestStatus.FAILED || status == TestStatus.ERROR) {
			message = Suites.firstText(test, "message");
			stacktrace = Suites.firstText(test, "stack-trace");
		}
		return new ParsedCase(className, className, name, status, durationMs, null, message, stacktrace, null, null,
				List.of());
	}

	private static TestStatus mapResult(String result) {
		return switch (result.toLowerCase(Locale.ROOT)) {
			case "pass" -> TestStatus.PASSED;
			case "fail" -> TestStatus.FAILED;
			case "skip" -> TestStatus.SKIPPED;
			default -> TestStatus.ERROR;
		};
	}

}
