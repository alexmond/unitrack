package org.alexmond.unitrack.ingest;

import org.springframework.stereotype.Component;

import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a JaCoCo XML report ({@code <report>} root). Reads the report-level counters for
 * aggregate coverage and the per-{@code <sourcefile>} counters for the file breakdown.
 * Packages are streamed one at a time (StAX) so a large report's whole tree never sits in
 * memory.
 */
@Component
public class JacocoXmlParser implements CoverageParser {

	@Override
	public String format() {
		return "jacoco";
	}

	@Override
	public boolean supports(String headSample) {
		return headSample.contains("<report");
	}

	@Override
	public CoverageResults parse(InputStream in) {
		try {
			XMLStreamReader reader = StaxXml.open(in);
			String root = StaxXml.nextStartElement(reader);
			if (!"report".equals(root)) {
				throw new IngestException("Not a JaCoCo report: root element is <" + root + ">");
			}

			Counters total = new Counters();
			List<CoverageResults.ParsedFileCoverage> files = new ArrayList<>();
			StaxXml.forEachChild(reader, (child) -> {
				switch (child.name()) {
					case "counter" -> applyCounter(child, total);
					case "package" -> collectPackage(child, files);
					default -> {
						// sessioninfo etc. — not tracked
					}
				}
			});
			reader.close();

			return new CoverageResults(total.lineCovered, total.lineMissed, total.branchCovered, total.branchMissed,
					total.instructionCovered, total.instructionMissed, total.methodCovered, total.methodMissed, files);
		}
		catch (IngestException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IngestException("Failed to parse JaCoCo XML: " + ex.getMessage(), ex);
		}
	}

	private void collectPackage(XmlNode pkg, List<CoverageResults.ParsedFileCoverage> files) {
		String packageName = pkg.attr("name");
		for (XmlNode sourceFile : pkg.children("sourcefile")) {
			Counters c = new Counters();
			for (XmlNode counter : sourceFile.children("counter")) {
				applyCounter(counter, c);
			}
			files.add(new CoverageResults.ParsedFileCoverage(packageName, sourceFile.attr("name"), c.lineCovered,
					c.lineMissed, c.branchCovered, c.branchMissed, uncoveredLines(sourceFile)));
		}
	}

	/**
	 * Line numbers with zero covered instructions ({@code ci="0"} and {@code mi>0}) — a
	 * fully-uncovered line, the clean signal for a "not covered" PR annotation (#443).
	 */
	private static List<Integer> uncoveredLines(XmlNode sourceFile) {
		List<Integer> lines = new ArrayList<>();
		for (XmlNode line : sourceFile.children("line")) {
			if (line.attrInt("ci", 0) == 0 && line.attrInt("mi", 0) > 0) {
				lines.add(line.attrInt("nr", 0));
			}
		}
		return lines;
	}

	private void applyCounter(XmlNode counter, Counters c) {
		String type = counter.attr("type");
		int missed = counter.attrInt("missed", 0);
		int covered = counter.attrInt("covered", 0);
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

	private static final class Counters {

		int lineCovered;

		int lineMissed;

		int branchCovered;

		int branchMissed;

		int instructionCovered;

		int instructionMissed;

		int methodCovered;

		int methodMissed;

	}

}
