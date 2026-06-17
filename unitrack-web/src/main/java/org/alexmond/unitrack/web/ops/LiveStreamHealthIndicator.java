package org.alexmond.unitrack.web.ops;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.web.live.LiveEventService;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Operational health of the live-updates (SSE) subsystem, on the Boot-4 health API. The
 * bean name ({@code liveStreamHealthIndicator}) derives the health component name
 * {@code liveStream} — we don't hard-name it. Always UP (the in-memory registry is always
 * serviceable); the connected-subscriber count is surfaced as a detail for operators.
 */
@Component
@RequiredArgsConstructor
public class LiveStreamHealthIndicator implements HealthIndicator {

	private final LiveEventService liveEvents;

	@Override
	public Health health() {
		return Health.up().withDetail("subscribers", this.liveEvents.subscriberCount()).build();
	}

}
