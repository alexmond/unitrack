package org.alexmond.unitrack.ingest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An immutable, in-memory snapshot of a single XML element subtree, materialized by
 * {@link StaxXml} one streaming unit at a time (a suite, test, package or module — never
 * the whole document). It exposes the read-only DOM-style accessors the report parsers
 * need, so per-record parsing logic stays close to the previous DOM code while overall
 * memory stays bounded to the largest single unit rather than the entire document (#369).
 *
 * <p>
 * {@link #attr(String)} mirrors DOM {@code getAttribute}: a missing attribute is the
 * empty string, never null.
 */
final class XmlNode {

	private final String name;

	private final Map<String, String> attrs;

	private final List<XmlNode> children;

	private final String text;

	XmlNode(String name, Map<String, String> attrs, List<XmlNode> children, String text) {
		this.name = name;
		this.attrs = attrs;
		this.children = children;
		this.text = text;
	}

	String name() {
		return this.name;
	}

	/**
	 * The attribute value, or {@code ""} when absent (DOM {@code getAttribute}
	 * semantics).
	 */
	String attr(String attribute) {
		return this.attrs.getOrDefault(attribute, "");
	}

	int attrInt(String attribute, int fallback) {
		String value = attr(attribute);
		if (value.isBlank()) {
			return fallback;
		}
		try {
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException ex) {
			return fallback;
		}
	}

	/** A seconds-valued attribute (e.g. {@code "1.234"}) in milliseconds. */
	long attrSecondsToMillis(String attribute) {
		return Suites.secondsToMillis(attr(attribute));
	}

	/** Direct child elements with the given local name. */
	List<XmlNode> children(String tag) {
		List<XmlNode> result = new ArrayList<>();
		for (XmlNode child : this.children) {
			if (child.name.equals(tag)) {
				result.add(child);
			}
		}
		return result;
	}

	/** The first direct child with the given local name, or null. */
	XmlNode child(String tag) {
		for (XmlNode child : this.children) {
			if (child.name.equals(tag)) {
				return child;
			}
		}
		return null;
	}

	/** All descendant elements (depth-first) with the given local name. */
	List<XmlNode> descendants(String tag) {
		List<XmlNode> result = new ArrayList<>();
		collectDescendants(tag, result);
		return result;
	}

	private void collectDescendants(String tag, List<XmlNode> out) {
		for (XmlNode child : this.children) {
			if (child.name.equals(tag)) {
				out.add(child);
			}
			child.collectDescendants(tag, out);
		}
	}

	/**
	 * All concatenated text content (this node and descendants), DOM
	 * {@code getTextContent}.
	 */
	String textContent() {
		if (this.children.isEmpty()) {
			return this.text;
		}
		StringBuilder sb = new StringBuilder(this.text);
		for (XmlNode child : this.children) {
			sb.append(child.textContent());
		}
		return sb.toString();
	}

	/** This node's full text content trimmed, or null when blank. */
	String trimmedTextOrNull() {
		String trimmed = textContent().strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/** The first descendant {@code <tag>}'s trimmed text, or null when absent/empty. */
	String firstDescendantText(String tag) {
		List<XmlNode> els = descendants(tag);
		return els.isEmpty() ? null : els.getFirst().trimmedTextOrNull();
	}

}
