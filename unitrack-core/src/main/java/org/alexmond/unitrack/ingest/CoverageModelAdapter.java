package org.alexmond.unitrack.ingest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.ModuleNode;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.util.FilteredLog;

/**
 * Bridges the coverage-model library (the Jenkins Coverage engine) into UniTrack's
 * {@link CoverageResults}. The per-format {@link CoverageParser}s delegate here, so the
 * four coverage formats share one battle-tested reader instead of four hand-rolled
 * parsers.
 */
final class CoverageModelAdapter {

	private CoverageModelAdapter() {
	}

	/**
	 * Parses {@code in} with a coverage-model parser and maps the tree to
	 * {@link CoverageResults}.
	 */
	static CoverageResults parse(edu.hm.hafner.coverage.CoverageParser parser, InputStream in, String label) {
		try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			ModuleNode module = parser.parse(reader, label, new FilteredLog("coverage:" + label));
			return toResults(module);
		}
		catch (IngestException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IngestException("Failed to parse " + label + " coverage: " + ex.getMessage(), ex);
		}
	}

	private static CoverageResults toResults(ModuleNode module) {
		Coverage line = cov(module, Metric.LINE);
		Coverage branch = cov(module, Metric.BRANCH);
		Coverage instruction = cov(module, Metric.INSTRUCTION);
		Coverage method = cov(module, Metric.METHOD);

		List<CoverageResults.ParsedFileCoverage> files = new ArrayList<>();
		for (FileNode file : module.getAllFileNodes()) {
			Coverage fileLine = cov(file, Metric.LINE);
			Coverage fileBranch = cov(file, Metric.BRANCH);
			// getMissedLines() = the fully-uncovered line numbers, for PR annotations
			// (#443).
			files.add(new CoverageResults.ParsedFileCoverage(packageName(file), fileName(file), fileLine.getCovered(),
					fileLine.getMissed(), fileBranch.getCovered(), fileBranch.getMissed(),
					new ArrayList<>(file.getMissedLines())));
		}
		return new CoverageResults(line.getCovered(), line.getMissed(), branch.getCovered(), branch.getMissed(),
				instruction.getCovered(), instruction.getMissed(), method.getCovered(), method.getMissed(), files);
	}

	private static Coverage cov(Node node, Metric metric) {
		return node.getTypedValue(metric, Coverage.nullObject(metric));
	}

	/** Prefer the relative path (e.g. {@code app/foo.py}) over the bare file name. */
	private static String fileName(FileNode file) {
		String rel = file.getRelativePath();
		return (rel != null && !rel.isBlank()) ? rel : file.getFileName();
	}

	/**
	 * A file's package is its parent {@link edu.hm.hafner.coverage.PackageNode}'s name.
	 */
	private static String packageName(FileNode file) {
		Node parent = file.getParent();
		return (parent != null) ? parent.getName() : "";
	}

}
