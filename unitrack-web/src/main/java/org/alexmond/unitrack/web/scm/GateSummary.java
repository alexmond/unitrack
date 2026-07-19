package org.alexmond.unitrack.web.scm;

import java.util.Locale;

import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.QualityGateResult;

/**
 * Renders the one-line gate summary that goes in a commit status' description. Every
 * provider shows the same sentence; they differ only in how much of it they accept, so
 * the limit is the caller's to pass.
 */
public final class GateSummary {

	private GateSummary() {
	}

	/**
	 * The gate verdict, test tallies and coverage as a single line — e.g.
	 * {@code Gate PASSED · 128 passed, 0 failed · cov 84.3% (+1.2pp)} — truncated to
	 * {@code maxLength} characters.
	 * @param run the run being reported
	 * @param gate its verdict, or null when no gate is configured
	 * @param coverageDelta coverage change vs the baseline in percentage points, or null
	 * @param maxLength the provider's limit for a status description
	 */
	public static String describe(TestRun run, QualityGateResult gate, Double coverageDelta, int maxLength) {
		StringBuilder sb = new StringBuilder("Gate ").append((gate != null) ? gate.status() : "n/a");
		sb.append(" · ")
			.append(run.getPassed())
			.append(" passed, ")
			.append(run.getFailed() + run.getErrors())
			.append(" failed");
		if (run.getLineCoveragePct() != null) {
			sb.append(" · cov ").append(String.format(Locale.ROOT, "%.1f%%", run.getLineCoveragePct()));
			if (coverageDelta != null) {
				sb.append(String.format(Locale.ROOT, " (%+.1fpp)", coverageDelta));
			}
		}
		return (sb.length() <= maxLength) ? sb.toString() : sb.substring(0, maxLength);
	}

}
