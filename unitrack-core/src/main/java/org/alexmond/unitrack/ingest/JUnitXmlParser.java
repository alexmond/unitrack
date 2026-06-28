package org.alexmond.unitrack.ingest;

import org.alexmond.unitrack.domain.TestStatus;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Surefire / JUnit XML reports. Handles both a {@code <testsuites>} root
 * containing many {@code <testsuite>} elements and a single {@code <testsuite>} root.
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
		try {
			Document doc = XmlSupport.parse(in);
			Element root = doc.getDocumentElement();
			List<Element> suiteElements = new ArrayList<>();
			if ("testsuites".equals(root.getNodeName())) {
				suiteElements.addAll(XmlSupport.children(root, "testsuite"));
			}
			else if ("testsuite".equals(root.getNodeName())) {
				suiteElements.add(root);
			}
			else {
				// Be lenient: search anywhere for testsuite elements.
				suiteElements.addAll(XmlSupport.descendants(root, "testsuite"));
			}

			List<ParsedSuite> suites = new ArrayList<>();
			for (Element suiteEl : suiteElements) {
				suites.add(parseSuite(suiteEl));
			}
			return new JUnitResults(suites);
		}
		catch (Exception ex) {
			throw new IngestException("Failed to parse JUnit XML: " + ex.getMessage(), ex);
		}
	}

	private ParsedSuite parseSuite(Element suiteEl) {
		String suiteName = suiteEl.getAttribute("name");
		List<ParsedCase> cases = new ArrayList<>();
		for (Element caseEl : XmlSupport.children(suiteEl, "testcase")) {
			cases.add(parseCase(suiteName, caseEl));
		}

		// Prefer explicit attributes; fall back to counting parsed cases.
		int tests = XmlSupport.attrInt(suiteEl, "tests", cases.size());
		int failures = XmlSupport.attrInt(suiteEl, "failures", (int) count(cases, TestStatus.FAILED));
		int errors = XmlSupport.attrInt(suiteEl, "errors", (int) count(cases, TestStatus.ERROR));
		int skipped = XmlSupport.attrInt(suiteEl, "skipped", (int) count(cases, TestStatus.SKIPPED));
		long durationMs = XmlSupport.attrSecondsToMillis(suiteEl, "time");
		return new ParsedSuite(suiteName, tests, failures, errors, skipped, durationMs, cases);
	}

	private ParsedCase parseCase(String suiteName, Element caseEl) {
		String className = caseEl.getAttribute("classname");
		String name = caseEl.getAttribute("name");
		long durationMs = XmlSupport.attrSecondsToMillis(caseEl, "time");

		String systemOut = firstText(caseEl, "system-out");
		String systemErr = firstText(caseEl, "system-err");
		List<String> attachments = extractAttachments(systemOut, systemErr);

		List<Element> failures = XmlSupport.children(caseEl, "failure");
		List<Element> errors = XmlSupport.children(caseEl, "error");
		List<Element> skipped = XmlSupport.children(caseEl, "skipped");

		TestStatus status = TestStatus.PASSED;
		Element detail = null;
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

		String type = (detail != null) ? emptyToNull(detail.getAttribute("type")) : null;
		String message = (detail != null) ? emptyToNull(detail.getAttribute("message")) : null;
		String stacktrace = (detail != null) ? emptyToNull(detail.getTextContent()) : null;
		return new ParsedCase(suiteName, className, name, status, durationMs, type, message, stacktrace, systemOut,
				systemErr, attachments);
	}

	private String firstText(Element caseEl, String tag) {
		List<Element> els = XmlSupport.children(caseEl, tag);
		return els.isEmpty() ? null : emptyToNull(els.getFirst().getTextContent());
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
