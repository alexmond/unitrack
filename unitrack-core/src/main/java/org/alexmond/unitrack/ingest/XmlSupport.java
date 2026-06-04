package org.alexmond.unitrack.ingest;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Hardened DOM parsing helpers shared by the report parsers. */
final class XmlSupport {

    private XmlSupport() {
    }

    /** Builds a {@link Document} with external entity/DTD resolution disabled (XXE-safe). */
    static Document parse(InputStream in) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(in);
        doc.getDocumentElement().normalize();
        return doc;
    }

    static List<Element> children(Element parent, String tag) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(tag)) {
                result.add((Element) node);
            }
        }
        return result;
    }

    static List<Element> descendants(Element parent, String tag) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            result.add((Element) nodes.item(i));
        }
        return result;
    }

    static int attrInt(Element el, String name, int fallback) {
        String value = el.getAttribute(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Parses a seconds-valued attribute (e.g. "1.234") into milliseconds. */
    static long attrSecondsToMillis(Element el, String name) {
        String value = el.getAttribute(name);
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Math.round(Double.parseDouble(value.trim()) * 1000.0);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
