package org.alexmond.unitrack.web.alert;

import org.alexmond.notify4j.NotificationAdapter;
import org.alexmond.unitrack.domain.AlertEvent;

/**
 * Teaches notify4j how to read UniTrack's {@link AlertEvent}: the project is the entity
 * whose status transitions are tracked, the {@link org.alexmond.unitrack.domain.AlertKind
 * kind} is the status, and the message is the human text. Used to build the per-project
 * delivery engine.
 */
public class AlertEventAdapter implements NotificationAdapter<AlertEvent> {

	@Override
	public Object id(AlertEvent event) {
		return event.projectId();
	}

	@Override
	public String status(AlertEvent event) {
		return event.kind().name();
	}

	@Override
	public String message(AlertEvent event) {
		return event.message();
	}

}
