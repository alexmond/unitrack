package org.alexmond.unitrack.ingest;

import java.io.InputStream;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Event-based (StAX) XML helpers shared by the report parsers. Streaming replaces the old
 * DOM approach so a multi-GB report no longer loads its whole tree into memory (#369);
 * parsers materialize only one {@link XmlNode} unit at a time (see
 * {@link #forEachSubtree} / {@link #forEachChild}).
 *
 * <p>
 * XXE-safe: external entity resolution and external DTD/schema fetching are disabled.
 * DOCTYPE declarations are still <em>permitted</em> (Cobertura reports ship a
 * {@code SYSTEM} doctype) — they are simply not resolved over the network.
 */
final class StaxXml {

	private StaxXml() {
	}

	private static XMLInputFactory hardenedFactory() {
		XMLInputFactory factory = XMLInputFactory.newFactory();
		// Permit DOCTYPE (Cobertura reports ship a SYSTEM doctype) and don't resolve
		// external general entities. The resolver below neutralizes any external DTD or
		// entity by returning an empty stream — so nothing is fetched over the network
		// (XXE-safe) and a report's harmless external DTD declaration doesn't break
		// parsing.
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, true);
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		factory.setProperty(XMLInputFactory.IS_COALESCING, true);
		factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> InputStream.nullInputStream());
		return factory;
	}

	static XMLStreamReader open(InputStream in) throws XMLStreamException {
		return hardenedFactory().createXMLStreamReader(in);
	}

	/** Advances to the first START_ELEMENT (the root) and returns its local name. */
	static String nextStartElement(XMLStreamReader reader) throws XMLStreamException {
		while (reader.hasNext()) {
			if (reader.next() == XMLStreamConstants.START_ELEMENT) {
				return reader.getLocalName();
			}
		}
		throw new IngestException("Empty XML document (no root element)");
	}

	/**
	 * Scans all remaining elements; whenever one whose local name is in {@code targets}
	 * starts, materializes its whole subtree and hands it to {@code handler}, then
	 * resumes after it. Non-target elements are descended into so nested targets are
	 * found; a target's own descendants are not scanned separately (the handler gets the
	 * subtree).
	 */
	static void forEachSubtree(XMLStreamReader reader, Set<String> targets, Consumer<XmlNode> handler)
			throws XMLStreamException {
		while (reader.hasNext()) {
			if (reader.next() == XMLStreamConstants.START_ELEMENT && targets.contains(reader.getLocalName())) {
				handler.accept(readSubtree(reader));
			}
		}
	}

	/**
	 * Iterates the direct child elements of the element the {@code reader} is currently
	 * positioned on (a START_ELEMENT), materializing each child subtree for
	 * {@code handler}, and returns when that element ends.
	 */
	static void forEachChild(XMLStreamReader reader, Consumer<XmlNode> handler) throws XMLStreamException {
		while (reader.hasNext()) {
			int event = reader.next();
			if (event == XMLStreamConstants.START_ELEMENT) {
				handler.accept(readSubtree(reader));
			}
			else if (event == XMLStreamConstants.END_ELEMENT) {
				return;
			}
		}
	}

	/**
	 * Materializes the element the {@code reader} is positioned on (a START_ELEMENT) and
	 * everything under it into an {@link XmlNode}, consuming through its END_ELEMENT.
	 */
	static XmlNode readSubtree(XMLStreamReader reader) throws XMLStreamException {
		String name = reader.getLocalName();
		Map<String, String> attrs = new HashMap<>();
		for (int i = 0; i < reader.getAttributeCount(); i++) {
			attrs.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
		}
		List<XmlNode> children = new ArrayList<>();
		StringBuilder text = new StringBuilder();
		while (reader.hasNext()) {
			int event = reader.next();
			switch (event) {
				case XMLStreamConstants.START_ELEMENT -> children.add(readSubtree(reader));
				case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> text.append(reader.getText());
				case XMLStreamConstants.END_ELEMENT -> {
					return new XmlNode(name, attrs, children, text.toString());
				}
				default -> {
					// comments / processing instructions / whitespace — ignored
				}
			}
		}
		return new XmlNode(name, attrs, children, text.toString());
	}

}
