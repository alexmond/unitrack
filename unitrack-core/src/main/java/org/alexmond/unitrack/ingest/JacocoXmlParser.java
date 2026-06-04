package org.alexmond.unitrack.ingest;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a JaCoCo XML report ({@code <report>} root). Reads the report-level counters for
 * aggregate coverage and the per-{@code <sourcefile>} counters for the file breakdown.
 */
@Component
public class JacocoXmlParser {

    public CoverageResults parse(InputStream in) {
        try {
            Document doc = XmlSupport.parse(in);
            Element report = doc.getDocumentElement();
            if (!"report".equals(report.getNodeName())) {
                throw new IngestException("Not a JaCoCo report: root element is <"
                        + report.getNodeName() + ">");
            }

            Counters total = readCounters(XmlSupport.children(report, "counter"));

            List<CoverageResults.ParsedFileCoverage> files = new ArrayList<>();
            for (Element pkg : XmlSupport.children(report, "package")) {
                String packageName = pkg.getAttribute("name");
                for (Element sourceFile : XmlSupport.children(pkg, "sourcefile")) {
                    Counters c = readCounters(XmlSupport.children(sourceFile, "counter"));
                    files.add(new CoverageResults.ParsedFileCoverage(
                            packageName,
                            sourceFile.getAttribute("name"),
                            c.lineCovered, c.lineMissed,
                            c.branchCovered, c.branchMissed));
                }
            }

            return new CoverageResults(
                    total.lineCovered, total.lineMissed,
                    total.branchCovered, total.branchMissed,
                    total.instructionCovered, total.instructionMissed,
                    total.methodCovered, total.methodMissed,
                    files);
        } catch (IngestException e) {
            throw e;
        } catch (Exception e) {
            throw new IngestException("Failed to parse JaCoCo XML: " + e.getMessage(), e);
        }
    }

    private Counters readCounters(List<Element> counterElements) {
        Counters c = new Counters();
        for (Element counter : counterElements) {
            String type = counter.getAttribute("type");
            int missed = XmlSupport.attrInt(counter, "missed", 0);
            int covered = XmlSupport.attrInt(counter, "covered", 0);
            switch (type) {
                case "LINE" -> {
                    c.lineCovered = covered;
                    c.lineMissed = missed;
                }
                case "BRANCH" -> {
                    c.branchCovered = covered;
                    c.branchMissed = missed;
                }
                case "INSTRUCTION" -> {
                    c.instructionCovered = covered;
                    c.instructionMissed = missed;
                }
                case "METHOD" -> {
                    c.methodCovered = covered;
                    c.methodMissed = missed;
                }
                default -> {
                    // CLASS, COMPLEXITY, etc. are not tracked.
                }
            }
        }
        return c;
    }

    private static final class Counters {
        int lineCovered, lineMissed;
        int branchCovered, branchMissed;
        int instructionCovered, instructionMissed;
        int methodCovered, methodMissed;
    }
}
