package org.alexmond.unitrack.report;

import java.util.Arrays;

/**
 * Detects a sustained level shift ("step") in a noisy metric series and locates when it
 * started — the "it always fluctuates, but it dropped to a worse level; when did that
 * happen?" problem for perf metrics (#379).
 *
 * <p>
 * Deliberately classical, transparent and tunable — no ML, no time-series DB:
 * <ul>
 * <li><b>Robust</b>: baseline level and spread are the <em>median</em> and <em>MAD</em>
 * (median absolute deviation), so right-skewed latencies and outliers (GC pauses, noisy
 * neighbours) don't distort the baseline.</li>
 * <li><b>Relative to the series' own noise</b>: the step is scored as a robust z —
 * {@code shift / (1.4826 · MAD)} — so a 5% drop is significant on a tight series and
 * noise on a jittery one.</li>
 * <li><b>Step, not spike</b>: the recent level is itself a median over a small window, so
 * a single transient spike can't trip it.</li>
 * <li><b>Onset via CUSUM</b>: a one-sided cumulative-sum walk finds where the sustained
 * climb away from baseline began.</li>
 * </ul>
 *
 * <p>
 * Direction-aware: pass {@code higherIsWorse=true} for latency/error metrics,
 * {@code false} for throughput. A statistical-significance gate (Mann–Whitney U) is a
 * documented follow-up; the robust-z threshold is the v1 confirmation.
 *
 * <p>
 * Scope: this finds <em>step</em> changes (a level that shifts and stays). A slow linear
 * <em>trend</em> with no distinct edge is intentionally not flagged — the baseline window
 * absorbs the climb — and is a separate detector (e.g. Mann-Kendall), left as a
 * follow-up.
 *
 * <p>
 * Stateless and pure — safe to share.
 */
public final class ChangePointDetector {

	/**
	 * @param values the series, oldest first
	 * @param higherIsWorse true for latency/error-rate, false for throughput
	 */
	public Result detect(double[] values, boolean higherIsWorse, Config cfg) {
		int n = values.length;
		if (n < cfg.minBaseline() + cfg.recentWindow()) {
			return new Result(Result.Status.INSUFFICIENT_DATA, -1, 0, Double.NaN, Double.NaN);
		}

		int split = n - cfg.recentWindow();
		double[] baseline = Arrays.copyOfRange(values, 0, split);
		double[] recent = Arrays.copyOfRange(values, split, n);
		double baselineMedian = median(baseline);
		double recentMedian = median(recent);

		// Spread on a robust scale; floored so a (near-)flat baseline doesn't divide by
		// ~0
		// yet a genuine step on a flat series still scores as significant.
		double scaled = Math.max(1.4826 * mad(baseline, baselineMedian),
				Math.max(0.01 * Math.abs(baselineMedian), 1e-9));

		double shift = higherIsWorse ? (recentMedian - baselineMedian) : (baselineMedian - recentMedian);
		double depthZ = shift / scaled;
		if (depthZ < cfg.zThreshold()) {
			return new Result(Result.Status.STABLE, -1, depthZ, baselineMedian, recentMedian);
		}

		int onset = cusumOnset(values, baselineMedian, scaled, higherIsWorse, cfg.slackMads());
		return new Result(Result.Status.REGRESSED, onset, depthZ, baselineMedian, recentMedian);
	}

	/**
	 * One-sided CUSUM onset: walk the deviation from baseline (in the worsening
	 * direction) with a slack dead-band; the onset is the first point after the walk last
	 * sat at zero — i.e. where the sustained climb that reaches the end began.
	 */
	private static int cusumOnset(double[] values, double baselineMedian, double scaled, boolean higherIsWorse,
			double slackMads) {
		double slack = slackMads * scaled;
		double sign = higherIsWorse ? 1.0 : -1.0;
		double sum = 0;
		int lastReset = -1;
		for (int i = 0; i < values.length; i++) {
			double deviation = sign * (values[i] - baselineMedian) - slack;
			sum = Math.max(0, sum + deviation);
			if (sum == 0) {
				lastReset = i;
			}
		}
		return Math.min(values.length - 1, Math.max(0, lastReset + 1));
	}

	private static double median(double[] values) {
		double[] sorted = values.clone();
		Arrays.sort(sorted);
		int n = sorted.length;
		return (n % 2 == 1) ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
	}

	/** Median absolute deviation from the given median. */
	private static double mad(double[] values, double median) {
		double[] deviations = new double[values.length];
		for (int i = 0; i < values.length; i++) {
			deviations[i] = Math.abs(values[i] - median);
		}
		return median(deviations);
	}

	/**
	 * @param minBaseline minimum baseline points required before any claim (new series
	 * stay quiet)
	 * @param recentWindow how many newest points form the "recent" level (median over
	 * them rejects a lone spike); ≥ 3 recommended
	 * @param zThreshold robust-z a shift must exceed to count as a regression
	 * @param slackMads CUSUM slack as a multiple of the scaled MAD (dead-band before the
	 * walk accumulates)
	 */
	public record Config(int minBaseline, int recentWindow, double zThreshold, double slackMads) {

		public static Config defaults() {
			return new Config(6, 3, 3.0, 0.5);
		}
	}

	/** Outcome of a detection. {@code onsetIndex} is meaningful only when REGRESSED. */
	public record Result(Status status, int onsetIndex, double depthZ, double baselineMedian, double recentMedian) {

		public enum Status {

			INSUFFICIENT_DATA, STABLE, REGRESSED

		}

		public boolean regressed() {
			return this.status == Status.REGRESSED;
		}
	}

}
