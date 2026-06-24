package org.alexmond.unitrack.web.alert;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.alexmond.notify4j.NotificationMetrics;

/**
 * Records notify4j's per-channel delivery outcomes as Micrometer counters
 * ({@code notify4j.notifications} tagged {@code channel} + {@code outcome}), so operators
 * can confirm alerts are going out (#300). notify4j-core has no metrics dependency — it
 * records against the {@link NotificationMetrics} SPI; UniTrack supplies this
 * implementation because it builds the factory itself rather than using the starter
 * (which only meters its own app-wide facade).
 */
@RequiredArgsConstructor
public class MicrometerNotificationMetrics implements NotificationMetrics {

	private final MeterRegistry registry;

	@Override
	public void recordSent(String channel) {
		count(channel, "sent");
	}

	@Override
	public void recordFailed(String channel) {
		count(channel, "failed");
	}

	@Override
	public void recordSuppressed(String channel) {
		count(channel, "suppressed");
	}

	private void count(String channel, String outcome) {
		this.registry.counter("notify4j.notifications", "channel", channel, "outcome", outcome).increment();
	}

}
