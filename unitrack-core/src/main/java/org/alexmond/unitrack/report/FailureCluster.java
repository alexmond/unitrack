package org.alexmond.unitrack.report;

import java.time.Instant;
import java.util.List;

/** A group of failures sharing a normalized signature (likely the same root cause). */
public record FailureCluster(String signature, String failureType, String sampleMessage, int occurrences,
		int distinctTests, List<String> tests, Instant lastSeen) {
}
