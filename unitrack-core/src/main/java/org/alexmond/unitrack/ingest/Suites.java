package org.alexmond.unitrack.ingest;

import java.util.List;

import org.alexmond.unitrack.domain.TestStatus;
import org.w3c.dom.Element;

/** Shared helpers for building {@link ParsedSuite}s from parsed cases. */
final class Suites {

	private Suites() {
	}

	/** A suite whose counts are derived from its cases. */
	static ParsedSuite of(String name, List<ParsedCase> cases) {
		int failures = (int) cases.stream().filter((c) -> c.status() == TestStatus.FAILED).count();
		int errors = (int) cases.stream().filter((c) -> c.status() == TestStatus.ERROR).count();
		int skipped = (int) cases.stream().filter((c) -> c.status() == TestStatus.SKIPPED).count();
		long durationMs = cases.stream().mapToLong(ParsedCase::durationMs).sum();
		return new ParsedSuite(name, cases.size(), failures, errors, skipped, durationMs, cases);
	}

	/** Decimal seconds (e.g. {@code "0.123"}) → milliseconds. */
	static long secondsToMillis(String seconds) {
		if (seconds == null || seconds.isBlank()) {
			return 0L;
		}
		try {
			return Math.round(Double.parseDouble(seconds.strip()) * 1000.0);
		}
		catch (NumberFormatException ex) {
			return 0L;
		}
	}

	/** First descendant {@code <tag>}'s trimmed text, or null when absent/empty. */
	static String firstText(Element parent, String tag) {
		List<Element> els = XmlSupport.descendants(parent, tag);
		if (els.isEmpty()) {
			return null;
		}
		String text = els.getFirst().getTextContent();
		if (text == null) {
			return null;
		}
		String trimmed = text.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
