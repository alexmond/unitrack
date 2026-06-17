package org.alexmond.unitrack.web.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.alexmond.unitrack.web.live.LiveEventService;
import org.springframework.stereotype.Component;

/**
 * Live operational gauges. A {@link MeterBinder} so Spring Boot binds them to every
 * registry (incl. Prometheus) — no manual registry wiring. Values are read on scrape:
 * subscribers from the in-memory registry, project/run counts via a cheap {@code COUNT}
 * query.
 */
@Component
@RequiredArgsConstructor
public class UnitrackMetrics implements MeterBinder {

	private final LiveEventService liveEvents;

	private final ProjectRepository projects;

	private final TestRunRepository runs;

	@Override
	public void bindTo(MeterRegistry registry) {
		Gauge.builder("unitrack.sse.subscribers", this.liveEvents, LiveEventService::subscriberCount)
			.description("Currently connected live (SSE) subscribers")
			.register(registry);
		Gauge.builder("unitrack.projects", this.projects, ProjectRepository::count)
			.description("Tracked projects")
			.register(registry);
		Gauge.builder("unitrack.runs", this.runs, TestRunRepository::count)
			.description("Stored test runs")
			.register(registry);
	}

}
