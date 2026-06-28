package org.alexmond.unitrack.web.ingest;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On startup, fails any ingest job left {@code QUEUED}/{@code PROCESSING} by a previous
 * run — the worker that owned it is gone, so it would otherwise be stuck in-flight
 * forever (#368). The persisted job row makes this recoverable across restarts.
 */
@Component
@RequiredArgsConstructor
class IngestJobRecovery {

	private static final Logger log = LoggerFactory.getLogger(IngestJobRecovery.class);

	private final IngestJobService ingestJobs;

	@EventListener(ApplicationReadyEvent.class)
	void recoverOnStartup() {
		int recovered = this.ingestJobs.recoverStuck();
		if (recovered > 0) {
			log.warn("Recovered {} ingest job(s) left in-flight by a previous run — marked FAILED", recovered);
		}
	}

}
