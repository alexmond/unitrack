package org.alexmond.unitrack.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Thresholds for slow-test (duration) regression detection, bound from
 * {@code unitrack.performance.*}. A test is flagged as a regression only when it crosses
 * both thresholds, so tiny/noisy timings are ignored.
 */
@Component
@ConfigurationProperties(prefix = "unitrack.performance")
@Getter
@Setter
public class PerformanceProperties {

	/** Branch whose latest run is the comparison baseline. */
	private String baseBranch = "main";

	/** Minimum slowdown vs the baseline (percent) to flag a test as regressed. */
	private double slowdownPct = 50.0;

	/** Minimum absolute slowdown (milliseconds) to flag a test — filters out noise. */
	private long slowdownMinMs = 50;

	/** Max allowed p95 latency increase vs the baseline perf run (percent). */
	private double latencyRegressionPct = 15.0;

	/** Max allowed throughput drop vs the baseline perf run (percent). */
	private double throughputDropPct = 10.0;

	/** Max allowed error rate for a perf run (percent). */
	private double maxErrorPct = 1.0;

}
