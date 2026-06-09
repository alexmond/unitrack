package org.alexmond.unitrack.ingest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Parses an OpenCover XML report ({@code <CoverageSession>} root), as produced by
 * OpenCover and Coverlet's {@code --format opencover}. Line coverage is derived from
 * {@code <SequencePoint>} visit counts grouped by source line; branches from
 * {@code <BranchPoint>} visit counts. Each method's {@code <FileRef>} resolves into the
 * module's {@code <File>} table to attribute coverage to a source file.
 *
 * <p>
 * (.NET coverage from Coverlet's default and Python coverage.py both emit Cobertura XML,
 * handled by {@link CoberturaXmlParser}; this parser covers the OpenCover variant.)
 */
@Component
public class OpenCoverXmlParser implements CoverageParser {

	@Override
	public String format() {
		return "opencover";
	}

	@Override
	public boolean supports(String headSample) {
		return headSample.contains("<CoverageSession");
	}

	@Override
	public CoverageResults parse(InputStream in) {
		try {
			Document doc = XmlSupport.parse(in);
			Element root = doc.getDocumentElement();
			if (!"CoverageSession".equals(root.getNodeName())) {
				throw new IngestException("Not an OpenCover report: root element is <" + root.getNodeName() + ">");
			}

			Map<String, FileAcc> byFile = new LinkedHashMap<>();
			for (Element module : XmlSupport.descendants(root, "Module")) {
				Map<String, String> filePaths = new HashMap<>();
				for (Element file : XmlSupport.descendants(module, "File")) {
					filePaths.put(file.getAttribute("uid"), file.getAttribute("fullPath"));
				}
				for (Element method : XmlSupport.descendants(module, "Method")) {
					accumulateMethod(method, filePaths, byFile);
				}
			}

			List<CoverageResults.ParsedFileCoverage> files = new ArrayList<>();
			int lc = 0;
			int lm = 0;
			int bc = 0;
			int bm = 0;
			for (Map.Entry<String, FileAcc> e : byFile.entrySet()) {
				FileAcc a = e.getValue();
				int fileLc = 0;
				int fileLm = 0;
				for (boolean covered : a.lines.values()) {
					if (covered) {
						fileLc++;
					}
					else {
						fileLm++;
					}
				}
				String[] pkgFile = splitPath(e.getKey());
				files.add(new CoverageResults.ParsedFileCoverage(pkgFile[0], pkgFile[1], fileLc, fileLm,
						a.branchCovered, a.branchMissed));
				lc += fileLc;
				lm += fileLm;
				bc += a.branchCovered;
				bm += a.branchMissed;
			}
			return new CoverageResults(lc, lm, bc, bm, 0, 0, 0, 0, files);
		}
		catch (IngestException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IngestException("Failed to parse OpenCover XML: " + ex.getMessage(), ex);
		}
	}

	private static void accumulateMethod(Element method, Map<String, String> filePaths, Map<String, FileAcc> byFile) {
		String uid = fileRefUid(method);
		if (uid == null) {
			return;
		}
		FileAcc acc = byFile.computeIfAbsent(filePaths.getOrDefault(uid, uid), (k) -> new FileAcc());
		for (Element sp : XmlSupport.descendants(method, "SequencePoint")) {
			int line = XmlSupport.attrInt(sp, "sl", 0);
			if (line > 0) {
				boolean covered = XmlSupport.attrInt(sp, "vc", 0) > 0;
				acc.lines.merge(line, covered, (a, b) -> a || b);
			}
		}
		for (Element bp : XmlSupport.descendants(method, "BranchPoint")) {
			if (XmlSupport.attrInt(bp, "vc", 0) > 0) {
				acc.branchCovered++;
			}
			else {
				acc.branchMissed++;
			}
		}
	}

	/**
	 * The {@code uid} of the method's first {@code <FileRef>}, or null if it has none.
	 */
	private static String fileRefUid(Element method) {
		List<Element> refs = XmlSupport.children(method, "FileRef");
		if (refs.isEmpty()) {
			return null;
		}
		String uid = refs.get(0).getAttribute("uid");
		return uid.isBlank() ? null : uid;
	}

	/**
	 * Splits a full file path into {@code [directory, fileName]} (handles both
	 * separators).
	 */
	private static String[] splitPath(String fullPath) {
		String normalized = fullPath.replace('\\', '/');
		int slash = normalized.lastIndexOf('/');
		if (slash < 0) {
			return new String[] { "", normalized };
		}
		return new String[] { normalized.substring(0, slash), normalized.substring(slash + 1) };
	}

	/** Per-file tally: each covered source line plus branch-point hits/misses. */
	private static final class FileAcc {

		private final Map<Integer, Boolean> lines = new LinkedHashMap<>();

		private int branchCovered;

		private int branchMissed;

	}

}
