package org.alexmond.unitrack.web.live;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.web.account.ProjectAccessService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent-Events stream of run-ingested updates. A client opens
 * {@code GET /api/v1/events} and receives {@code run} events as projects it can see get
 * new runs. The stream connects as the current user (or anonymously), and the broadcast
 * layer filters per project visibility.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LiveEventsController {

	private final LiveEventService liveEvents;

	private final ProjectAccessService access;

	@GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter events() {
		return this.liveEvents.subscribe(this.access.currentUsername());
	}

}
