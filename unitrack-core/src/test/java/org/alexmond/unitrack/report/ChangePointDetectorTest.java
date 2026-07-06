package org.alexmond.unitrack.report;

import java.util.Arrays;

import org.alexmond.unitrack.report.ChangePointDetector.Config;
import org.alexmond.unitrack.report.ChangePointDetector.Result;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChangePointDetectorTest {

	private final ChangePointDetector detector = new ChangePointDetector();

	private final Config cfg = Config.defaults();

	/** {@code n} points jittering ±jitter around base (deterministic, no RNG). */
	private static double[] noisy(int n, double base, double jitter, int seed) {
		double[] v = new double[n];
		for (int i = 0; i < n; i++) {
			// deterministic pseudo-jitter from a cheap hash, in [-jitter, +jitter]
			double f = ((((i + seed) * 2654435761L) & 0xffff) / 65535.0) * 2 - 1;
			v[i] = base + f * jitter;
		}
		return v;
	}

	private static double[] concat(double[] a, double[] b) {
		double[] out = Arrays.copyOf(a, a.length + b.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}

	@Test
	void flatSeriesIsStable() {
		Result r = this.detector.detect(noisy(20, 100, 5, 1), true, this.cfg);
		assertThat(r.status()).isEqualTo(Result.Status.STABLE);
		assertThat(r.regressed()).isFalse();
	}

	@Test
	void stepUpInLatencyIsRegressedWithOnsetAtTheStep() {
		// 12 points ~100ms, then 6 points ~160ms (a sustained ~60% jump).
		double[] series = concat(noisy(12, 100, 4, 1), noisy(6, 160, 4, 7));
		Result r = this.detector.detect(series, true, this.cfg);
		assertThat(r.regressed()).isTrue();
		assertThat(r.depthZ()).isGreaterThan(this.cfg.zThreshold());
		assertThat(r.onsetIndex()).isBetween(11, 13); // step begins at index 12
		assertThat(r.recentMedian()).isGreaterThan(r.baselineMedian());
	}

	@Test
	void loneSpikeIsNotARegression() {
		double[] series = noisy(20, 100, 4, 1);
		series[18] = 400; // single transient spike near the end
		Result r = this.detector.detect(series, true, this.cfg);
		assertThat(r.regressed()).isFalse();
	}

	@Test
	void throughputDropIsRegressedWhenLowerIsWorse() {
		// req/s holds ~1000, then falls to ~600.
		double[] series = concat(noisy(12, 1000, 20, 1), noisy(6, 600, 20, 7));
		Result r = this.detector.detect(series, false, this.cfg);
		assertThat(r.regressed()).isTrue();
		assertThat(r.recentMedian()).isLessThan(r.baselineMedian());
		assertThat(r.onsetIndex()).isBetween(11, 13);
	}

	@Test
	void aDropThatRecoversReadsAsStableAtTheTail() {
		// dipped in the middle but back to baseline recently — recent window is healthy.
		double[] series = concat(concat(noisy(8, 100, 4, 1), noisy(4, 160, 4, 5)), noisy(8, 100, 4, 9));
		Result r = this.detector.detect(series, true, this.cfg);
		assertThat(r.regressed()).isFalse();
	}

	@Test
	void aRampThatSettlesAtAWorseLevelIsRegressed() {
		// flat low, a short climb, then sustained high — i.e. a step with a soft edge.
		double[] series = concat(concat(noisy(8, 100, 3, 1), new double[] { 112, 124, 136 }), noisy(5, 140, 3, 9));
		Result r = this.detector.detect(series, true, this.cfg);
		assertThat(r.regressed()).isTrue();
	}

	@Test
	void pureLinearDriftIsNotTreatedAsAStep() {
		// A steady ramp across the whole window is a TREND, not a level shift: the
		// baseline
		// window already contains the climb, so its MAD absorbs it. v1 detects steps;
		// slow-trend detection (e.g. Mann-Kendall) is a documented follow-up.
		double[] series = new double[18];
		for (int i = 0; i < series.length; i++) {
			series[i] = 100 + i * 8.0;
		}
		Result r = this.detector.detect(series, true, this.cfg);
		assertThat(r.regressed()).isFalse();
	}

	@Test
	void tooFewPointsIsInsufficientData() {
		Result r = this.detector.detect(noisy(5, 100, 4, 1), true, this.cfg);
		assertThat(r.status()).isEqualTo(Result.Status.INSUFFICIENT_DATA);
	}

	@Test
	void aGenuineStepOnAPerfectlyFlatSeriesStillFires() {
		double[] series = new double[16];
		Arrays.fill(series, 0, 12, 50.0);
		Arrays.fill(series, 12, 16, 56.0); // a clean ~12% step, zero prior noise
		Result r = this.detector.detect(series, true, this.cfg);
		assertThat(r.regressed()).isTrue();
		assertThat(r.onsetIndex()).isBetween(11, 13);
	}

}
