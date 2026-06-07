package org.alexmond.unitrack.ingest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Parses an LCOV {@code .info} report (the format emitted by genhtml/lcov, Istanbul,
 * llvm-cov, coverage.py via lcov, etc.). Counts lines from {@code DA:}, branches from
 * {@code BRDA:} and methods from {@code FNDA:} records; instruction coverage is not
 * represented.
 */
@Component
public class LcovParser implements CoverageParser {

	@Override
	public String format() {
		return "lcov";
	}

	@Override
	public boolean supports(String headSample) {
		return headSample.contains("SF:") && !headSample.contains("<");
	}

	@Override
	public CoverageResults parse(InputStream in) {
		List<CoverageResults.ParsedFileCoverage> files = new ArrayList<>();
		int lc = 0;
		int lm = 0;
		int bc = 0;
		int bm = 0;
		int mc = 0;
		int mm = 0;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			Record rec = new Record();
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("SF:")) {
					rec = new Record();
					rec.path = line.substring(3);
				}
				else if (line.startsWith("DA:")) {
					rec.lineTotal++;
					if (hitsAfterComma(line) > 0) {
						rec.lineCovered++;
					}
				}
				else if (line.startsWith("BRDA:")) {
					rec.branchTotal++;
					if (branchTaken(line)) {
						rec.branchCovered++;
					}
				}
				else if (line.startsWith("FNDA:")) {
					rec.methodTotal++;
					if (hitsAfterColon(line) > 0) {
						rec.methodCovered++;
					}
				}
				else if (line.equals("end_of_record") && rec.path != null) {
					files.add(rec.toFileCoverage());
					lc += rec.lineCovered;
					lm += rec.lineTotal - rec.lineCovered;
					bc += rec.branchCovered;
					bm += rec.branchTotal - rec.branchCovered;
					mc += rec.methodCovered;
					mm += rec.methodTotal - rec.methodCovered;
					rec = new Record();
				}
			}
			return new CoverageResults(lc, lm, bc, bm, 0, 0, mc, mm, files);
		}
		catch (Exception ex) {
			throw new IngestException("Failed to parse LCOV report: " + ex.getMessage(), ex);
		}
	}

	/** {@code DA:<line>,<hits>[,checksum]} — the hit count after the first comma. */
	private static long hitsAfterComma(String line) {
		String[] parts = line.substring(3).split(",");
		return (parts.length >= 2) ? parseLong(parts[1]) : 0L;
	}

	/** {@code FNDA:<hits>,<name>} — the hit count after the colon. */
	private static long hitsAfterColon(String line) {
		String[] parts = line.substring(5).split(",");
		return (parts.length >= 1) ? parseLong(parts[0]) : 0L;
	}

	/**
	 * {@code BRDA:<line>,<block>,<branch>,<taken>} — taken is "-" (not hit) or a count.
	 */
	private static boolean branchTaken(String line) {
		String[] parts = line.substring(5).split(",");
		return parts.length >= 4 && !"-".equals(parts[3]) && parseLong(parts[3]) > 0;
	}

	private static long parseLong(String value) {
		try {
			return Long.parseLong(value.trim());
		}
		catch (NumberFormatException ex) {
			return 0L;
		}
	}

	/** Mutable per-{@code SF:} accumulator. */
	private static final class Record {

		private String path;

		private int lineCovered;

		private int lineTotal;

		private int branchCovered;

		private int branchTotal;

		private int methodCovered;

		private int methodTotal;

		private CoverageResults.ParsedFileCoverage toFileCoverage() {
			int slash = this.path.lastIndexOf('/');
			String packageName = (slash > 0) ? this.path.substring(0, slash) : "";
			String fileName = (slash >= 0) ? this.path.substring(slash + 1) : this.path;
			return new CoverageResults.ParsedFileCoverage(packageName, fileName, this.lineCovered,
					this.lineTotal - this.lineCovered, this.branchCovered, this.branchTotal - this.branchCovered);
		}

	}

}
