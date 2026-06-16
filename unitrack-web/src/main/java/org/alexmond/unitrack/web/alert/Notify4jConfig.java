package org.alexmond.unitrack.web.alert;

import java.util.List;

import org.alexmond.notify4j.NotificationsFactory;
import org.alexmond.unitrack.domain.AlertEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires notify4j as the alert delivery engine. We build a typed
 * {@link NotificationsFactory} ourselves (rather than via the starter's global one)
 * because UniTrack routes per project: the factory turns a project's channel URLs into a
 * {@link org.alexmond.notify4j.Notifications} on demand. {@code includeLog=false} —
 * UniTrack already has its own logging sink.
 */
@Configuration
public class Notify4jConfig {

	@Bean
	public NotificationsFactory<AlertEvent> alertNotificationsFactory() {
		return new NotificationsFactory<>(new AlertEventAdapter(), List.of(), false);
	}

}
