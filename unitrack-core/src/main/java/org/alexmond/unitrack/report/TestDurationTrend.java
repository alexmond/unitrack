package org.alexmond.unitrack.report;

import java.util.List;

/**
 * A single test's duration over recent runs (oldest first), for the per-test trend chart.
 */
public record TestDurationTrend(String className, String name, List<DurationPoint> points) {
}
