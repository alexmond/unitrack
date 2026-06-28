package org.alexmond.unitrack.ingest;

import org.alexmond.unitrack.domain.TestStatus;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Surefire / JUnit XML reports. Handles both a {@code <testsuites>} root
 * containing many {@code <testsuite>} elements and a single {@code <testsuite>} root.
 * Each {@code <testsuite>} is streamed and parsed one at a time (StAX), so a large merged
 * report doesn't load its whole DOM tree into memory.
 */
@Component
public class JUnitXmlParser implements TestResultParser {

	/**
	 * JUnit 5 {@code publishEntry} attachment marker, e.g.
	 * {@code [[ATTACHMENT|screenshot.png]]}.
	 */
	private static final Pattern ATTACHMENT_MARKER = Pattern.compile("\\[\\[ATTACHMENT\\|([^\\]]+)\\]\\]");

	@Override
	public String format() {
		return "junit";
	}

	@Override
	public boolean supports(String headSample) {
		return headSample.contains("<testsuite");
	}

	@Override
	public JUnitResults parse(InputStream in) {
		List<ParsedSuite> suites = new ArrayList<>();
		try {
			XMLStreamReader reader = StaxXml.open(in);
			StaxXml.forEachSubtree(reader, Set.of("testsuite"), (suite) -> suites.add(parseSuite(suite)));
			reader.close();
			return new JUnitResults(suites);
		}
		catch (Exception ex) {
			throw new IngestException("Failed to parse JUnit XML: " + ex.getMessage(), ex);
		}
	}

	private ParsedSuite parseSuite(XmlNode suiteEl) {
		String suiteName = suiteEl.attr("name");
		List<ParsedCase> cases = new ArrayList<>();
		for (XmlNode caseEl : suiteEl.children("testcase")) {
			cases.add(parseCase(suiteName, caseEl));
		}

		// Prefer explicit attributes; fall back to counting parsed cases.
		int tests = suiteEl.attrInt("tests", cases.size());
		int failures = suiteEl.attrInt("failures", (int) count(cases, TestStatus.FAILED));
		int errors = suiteEl.attrInt("errors", (int) count(cases, TestStatus.ERROR));
		int skipped = suiteEl.attrInt("skipped", (int) count(cases, TestStatus.SKIPPED));
		long durationMs = suiteEl.attrSecondsToMillis("time");
		return new ParsedSuite(suiteName, tests, failures, errors, skipped, durationMs, cases);
	}

	private ParsedCase parseCase(String suiteName, XmlNode caseEl) {
		String className = caseEl.attr("classname");
		String name = caseEl.attr("name");
		long durationMs = caseEl.attrSecondsToMillis("time");

		String systemOut = firstText(caseEl, "system-out");
		String systemErr = firstText(caseEl, "system-err");
		List<String> attachments = extractAttachments(systemOut, systemErr);

		List<XmlNode> failures = caseEl.children("failure");
		List<XmlNode> errors = caseEl.children("error");
		List<XmlNode> skipped = caseEl.children("skipped");

		TestStatus status = TestStatus.PASSED;
		XmlNode detail = null;
		if (!errors.isEmpty()) {
			status = TestStatus.ERROR;
			detail = errors.getFirst();
		}
		else if (!failures.isEmpty()) {
			status = TestStatus.FAILED;
			detail = failures.getFirst();
		}
		else if (!skipped.isEmpty()) {
			status = TestStatus.SKIPPED;
		}

		String type = (detail != null) ? emptyToNull(detail.attr("type")) : null;
		String message = (detail != null) ? emptyToNull(detail.attr("message")) : null;
		String stacktrace = (detail != null) ? detail.trimmedTextOrNull() : null;
		return new ParsedCase(suiteName, className, name, status, durationMs, type, message, stacktrace, systemOut,
				systemErr, attachments);
	}

	private String firstText(XmlNode caseEl, String tag) {
		XmlNode el = caseEl.child(tag);
		return (el == null) ? null : el.trimmedTextOrNull();
	}

	private List<String> extractAttachments(String... texts) {
		List<String> urls = new ArrayList<>();
		for (String text : texts) {
			if (text == null) {
				continue;
			}
			Matcher matcher = ATTACHMENT_MARKER.matcher(text);
			while (matcher.find()) {
				urls.add(matcher.group(1).trim());
			}
		}
		return urls;
	}

	private static long count(List<ParsedCase> cases, TestStatus status) {
		return cases.stream().filter((c) -> c.status() == status).count();
	}

	private static String emptyToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
