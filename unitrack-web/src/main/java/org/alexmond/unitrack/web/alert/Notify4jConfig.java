package org.alexmond.unitrack.web.alert;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.alexmond.notify4j.HttpClientConfig;
import org.alexmond.notify4j.NotificationMetrics;
import org.alexmond.notify4j.NotificationsConfig;
import org.alexmond.notify4j.NotificationsFactory;
import org.alexmond.unitrack.domain.AlertEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires notify4j as the alert delivery engine. We build a typed
 * {@link NotificationsFactory} ourselves (rather than via the starter's global one)
 * because UniTrack routes per project: the factory turns a project's channel URLs into a
 * {@link org.alexmond.notify4j.Notifications} on demand. The starter only auto-configures
 * async/retry/metrics on <em>its</em> app-wide facade, so we pass the equivalent hooks
 * ourselves (#300): a shared {@link ExecutorService} for non-blocking delivery (keeps a
 * slow channel off the ingest thread), an {@link HttpClientConfig} with timeouts +
 * transient-failure retry, and a Micrometer {@link NotificationMetrics} sink.
 * {@code includeLog=false} — UniTrack already has its own logging sink.
 */
@Configuration
@RequiredArgsConstructor
public class Notify4jConfig {

	private final AlertProperties props;

	/**
	 * Shared pool for async channel delivery. Daemon threads + {@code CallerRunsPolicy}
	 * so a flood degrades to synchronous rather than rejecting or exhausting memory.
	 */
	@Bean(destroyMethod = "shutdown")
	ExecutorService alertDeliveryExecutor() {
		int size = Math.max(1, this.props.getDelivery().getPoolSize());
		AtomicInteger n = new AtomicInteger();
		return new ThreadPoolExecutor(size, size, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(256), (r) -> {
			Thread t = new Thread(r, "alert-delivery-" + n.incrementAndGet());
			t.setDaemon(true);
			return t;
		}, new ThreadPoolExecutor.CallerRunsPolicy());
	}

	@Bean
	public NotificationsFactory<AlertEvent> alertNotificationsFactory(ExecutorService alertDeliveryExecutor,
			ObjectProvider<MeterRegistry> meterRegistry) {
		AlertProperties.Delivery d = this.props.getDelivery();
		HttpClientConfig http = HttpClientConfig.of(d.getConnectTimeout(), d.getReadTimeout(), d.getMaxAttempts(),
				d.getRetryBackoff());
		MeterRegistry registry = meterRegistry.getIfAvailable();
		NotificationMetrics metrics = (registry != null) ? new MicrometerNotificationMetrics(registry)
				: NotificationMetrics.NOOP;
		// notify4j 0.8 takes a NotificationsConfig (builder) instead of the old
		// positional
		// constructor — bundles includeLog/http/executor/metrics; the executor stays
		// caller-owned.
		NotificationsConfig config = NotificationsConfig.builder()
			.includeLog(false)
			.http(http)
			.executor(alertDeliveryExecutor)
			.metrics(metrics)
			.build();
		return new NotificationsFactory<>(new AlertEventAdapter(), config);
	}

}
