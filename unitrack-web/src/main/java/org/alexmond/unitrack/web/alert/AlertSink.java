package org.alexmond.unitrack.web.alert;

import org.alexmond.unitrack.domain.AlertEvent;

/**
 * Receives emitted {@link AlertEvent}s. The seam between event production (this epic,
 * done now) and delivery: the routing slice (#242) adds a sink that resolves the
 * project's channels and dispatches through the shared notification library (#238). A
 * sink must not throw — the publisher isolates failures, but sinks should be best-effort
 * too.
 */
@FunctionalInterface
public interface AlertSink {

	void publish(AlertEvent event);

}
