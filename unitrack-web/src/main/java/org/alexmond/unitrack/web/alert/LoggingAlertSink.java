package org.alexmond.unitrack.web.alert;

import org.alexmond.unitrack.domain.AlertEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default sink: logs each event. Always present so emission is observable before the
 * routing slice wires real channel delivery.
 */
@Component
public class LoggingAlertSink implements AlertSink {

	private static final Logger log = LoggerFactory.getLogger(LoggingAlertSink.class);

	@Override
	public void publish(AlertEvent event) {
		log.info("alert [{}] project={} run={}: {}", event.kind(), event.projectName(), event.runId(), event.message());
	}

}
