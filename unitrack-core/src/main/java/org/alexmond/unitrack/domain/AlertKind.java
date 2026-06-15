package org.alexmond.unitrack.domain;

/**
 * The kind of an {@link AlertEvent}. Used as the routing tag on a project's alert
 * channels — a channel tagged {@code GATE_FAILED} receives only gate-failure alerts.
 */
public enum AlertKind {

	GATE_FAILED, NEW_REGRESSION, COVERAGE_DROPPED, FLAKY_PROMOTED

}
