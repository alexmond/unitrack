package org.alexmond.unitrack.domain;

/**
 * An alertable thing that happened to a project on a run (gate failed, new regression,
 * coverage dropped, …). Carries just enough to render and route a notification; the
 * delivery layer turns it into channel-specific payloads. {@code runId} may be null for
 * non-run-scoped events.
 */
public record AlertEvent(Long projectId, String projectName, AlertKind kind, Long runId, String message) {
}
