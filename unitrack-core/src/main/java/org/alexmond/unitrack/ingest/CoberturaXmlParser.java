package org.alexmond.unitrack.ingest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLStreamReader;

import org.springframework.stereotype.Component;

/**
 * Parses a Cobertura XML report ({@code <coverage>} root). Counts covered/missed lines
 * from each {@code <line hits=..>} and branches from the
 * {@code condition-coverage="x% (a/b)"} attribute. Cobertura carries no
 * instruction/method counters, so those are left at zero. Packages are streamed one at a
 * time (StAX), accumulating per source file across the whole report.
 */
@Component
public class CoberturaXmlParser implements CoverageParser {

	@Override
	public String format() {
		return "cobertura";
	}

	@Override
	public boolean supports(String headSample) {
		return headSample.contains("<coverage");
	}

	@Override
	public CoverageResults parse(InputStream in) {
		try {
			XMLStreamReader reader = StaxXml.open(in);
			String root = StaxXml.nextStartElement(reader);
			if (!"coverage".equals(root)) {
				throw new IngestException("Not a Cobertura report: root element is <" + root + ">");
			}

			// Aggregate per source file (inner classes share a filename).
			Map<String, int[]> byFile = new LinkedHashMap<>();
			Map<String, String> packageOf = new LinkedHashMap<>();
			StaxXml.forEachSubtree(reader, Set.of("package"), (pkg) -> collectPackage(pkg, byFile, packageOf));
			reader.close();

			List<CoverageResults.ParsedFileCoverage> files = new ArrayList<>();
			int lc = 0;
			int lm = 0;
			int bc = 0;
			int bm = 0;
			for (Map.Entry<String, int[]> e : byFile.entrySet()) {
				int[] a = e.getValue();
				String packageName = packageOf.get(e.getKey());
				String fileName = e.getKey().substring(e.getKey().indexOf(' ') + 1);
				files.add(new CoverageResults.ParsedFileCoverage(packageName, fileName, a[0], a[1], a[2], a[3]));
				lc += a[0];
				lm += a[1];
				bc += a[2];
				bm += a[3];
			}
			return new CoverageResults(lc, lm, bc, bm, 0, 0, 0, 0, files);
		}
		catch (IngestException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IngestException("Failed to parse Cobertura XML: " + ex.getMessage(), ex);
		}
	}

	private static void collectPackage(XmlNode pkg, Map<String, int[]> byFile, Map<String, String> packageOf) {
		String packageName = pkg.attr("name");
		for (XmlNode cls : pkg.descendants("class")) {
			String fileName = cls.attr("filename");
			String fkey = packageName + ' ' + fileName;
			int[] acc = byFile.computeIfAbsent(fkey, (k) -> new int[4]);
			packageOf.putIfAbsent(fkey, packageName);
			// Only the class's direct <lines>; per-<method> <lines> duplicate these.
			for (XmlNode lines : cls.children("lines")) {
				for (XmlNode line : lines.children("line")) {
					accumulateLine(line, acc);
				}
			}
		}
	}

	/**
	 * Updates {@code [lineCovered, lineMissed, branchCovered, branchMissed]} for one
	 * line.
	 */
	private static void accumulateLine(XmlNode line, int[] acc) {
		int hits = line.attrInt("hits", 0);
		if (hits > 0) {
			acc[0]++;
		}
		else {
			acc[1]++;
		}
		if ("true".equals(line.attr("branch"))) {
			int[] cb = parseConditionCoverage(line.attr("condition-coverage"));
			acc[2] += cb[0];
			acc[3] += cb[1] - cb[0];
		}
	}

	/**
	 * Parses {@code "75% (3/4)"} into {@code [covered, total]}; {@code [0,0]} if absent.
	 */
	private static int[] parseConditionCoverage(String value) {
		if (value == null) {
			return new int[] { 0, 0 };
		}
		int open = value.indexOf('(');
		int slash = value.indexOf('/');
		int close = value.indexOf(')');
		if (open < 0 || slash < 0 || close < 0 || slash < open || close < slash) {
			return new int[] { 0, 0 };
		}
		try {
			int covered = Integer.parseInt(value.substring(open + 1, slash).trim());
			int total = Integer.parseInt(value.substring(slash + 1, close).trim());
			return new int[] { covered, total };
		}
		catch (NumberFormatException ex) {
			return new int[] { 0, 0 };
		}
	}

}
