package org.alexmond.unitrack.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tunable thresholds for the quality gate, bound from {@code unitrack.quality-gate.*}.
 */
@Component
@ConfigurationProperties(prefix = "unitrack.quality-gate")
@Getter
@Setter
public class QualityGateProperties {

	/** Branch whose latest run is the comparison baseline. */
	private String baseBranch = "main";

	/** Absolute minimum line coverage percentage; null disables the rule. */
	private Double minLineCoverage;

	/** Maximum allowed line-coverage drop (percentage points) vs the baseline. */
	private double maxCoverageDropPct = 1.0;

	/** Fail when new, non-quarantined test failures appear relative to the baseline. */
	private boolean failOnNewFailures = true;

}
