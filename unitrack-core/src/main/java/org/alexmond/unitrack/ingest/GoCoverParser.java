package org.alexmond.unitrack.ingest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Parses a Go coverage profile ({@code go test -coverprofile=cover.out} / {@code go tool
 * cover}). Format: a {@code mode: set|count|atomic} header, then one block per line —
 * {@code file:startLine.col,endLine.col numStatements count}. Statements with a non-zero
 * count are covered; the rest are missed. Go has no branch/method counters, so only line
 * (statement) coverage is reported.
 */
@Component
public class GoCoverParser implements CoverageParser {

	@Override
	public String format() {
		return "go-cover";
	}

	@Override
	public boolean supports(String headSample) {
		String h = headSample.stripLeading();
		return h.startsWith("mode:")
				&& (h.contains("mode: set") || h.contains("mode: count") || h.contains("mode: atomic"));
	}

	@Override
	public CoverageResults parse(InputStream in) {
		Map<String, int[]> perFile = new LinkedHashMap<>(); // file -> [covered, missed]
		int totalCovered = 0;
		int totalMissed = 0;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.strip();
				if (line.isEmpty() || line.startsWith("mode:")) {
					continue;
				}
				// file:s.c,e.c numStatements count
				String[] parts = line.split("\\s+");
				if (parts.length < 3) {
					continue;
				}
				int colon = parts[0].lastIndexOf(':');
				if (colon <= 0) {
					continue;
				}
				String file = parts[0].substring(0, colon);
				int statements = parseIntOr(parts[parts.length - 2], 0);
				int count = parseIntOr(parts[parts.length - 1], 0);
				int[] cm = perFile.computeIfAbsent(file, (k) -> new int[2]);
				if (count > 0) {
					cm[0] += statements;
					totalCovered += statements;
				}
				else {
					cm[1] += statements;
					totalMissed += statements;
				}
			}
		}
		catch (java.io.IOException ex) {
			throw new IngestException("Failed to parse Go coverage profile: " + ex.getMessage(), ex);
		}
		if (perFile.isEmpty()) {
			throw new IngestException("No coverage blocks found in Go coverage profile");
		}
		List<CoverageResults.ParsedFileCoverage> files = new ArrayList<>();
		for (Map.Entry<String, int[]> e : perFile.entrySet()) {
			files.add(new CoverageResults.ParsedFileCoverage(packageOf(e.getKey()), fileOf(e.getKey()), e.getValue()[0],
					e.getValue()[1], 0, 0));
		}
		return new CoverageResults(totalCovered, totalMissed, 0, 0, 0, 0, 0, 0, files);
	}

	private static String packageOf(String path) {
		int slash = path.lastIndexOf('/');
		return (slash >= 0) ? path.substring(0, slash) : "";
	}

	private static String fileOf(String path) {
		int slash = path.lastIndexOf('/');
		return (slash >= 0) ? path.substring(slash + 1) : path;
	}

	private static int parseIntOr(String s, int fallback) {
		try {
			return Integer.parseInt(s.strip());
		}
		catch (NumberFormatException ex) {
			return fallback;
		}
	}

}
