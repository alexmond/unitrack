package org.alexmond.unitrack.web.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Observability #1: the single ingest Observation yields a derived Timer (with tags), the
 * MeterBinder gauges are bound, and everything surfaces in the Prometheus exposition
 * (what {@code /actuator/prometheus} serves).
 */
@SpringBootTest
class MetricsIntegrationTest {

	private static final byte[] JUNIT = ("<?xml version=\"1.0\"?>"
			+ "<testsuite name=\"com.x.S\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\" time=\"0.01\">"
			+ "<testcase name=\"ok\" classname=\"com.x.S\" time=\"0.01\"/></testsuite>")
		.getBytes();

	private static final byte[] JACOCO = ("<?xml version=\"1.0\"?><report name=\"r\">"
			+ "<counter type=\"LINE\" missed=\"3\" covered=\"7\"/>"
			+ "<package name=\"p\"><sourcefile name=\"F.java\"><counter type=\"LINE\" missed=\"3\" covered=\"7\"/>"
			+ "</sourcefile></package></report>")
		.getBytes();

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private MeterRegistry registry;

	@Autowired
	private PrometheusMeterRegistry prometheus;

	@Test
	void ingestObservationDerivesATaggedTimerAndGaugesAreScrapable() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(this.context).build();
		mvc.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST.xml", "text/xml", JUNIT))
			.file(new MockMultipartFile("jacoco", "jacoco.xml", "text/xml", JACOCO))
			.param("project", "metrics-demo")
			.param("commit", "c1")).andExpect(status().isCreated());

		// The Observation auto-derived a Timer named after it, tagged with
		// low-cardinality
		// dimensions (result + has_coverage), and recorded the run.
		Timer timer = this.registry.get("unitrack.ingest").tag("result", "PASSED").tag("has_coverage", "true").timer();
		assertThat(timer.count()).isGreaterThanOrEqualTo(1);

		// MeterBinder gauges are bound to the registry.
		assertThat(this.registry.find("unitrack.sse.subscribers").gauge()).isNotNull();
		assertThat(this.registry.find("unitrack.projects").gauge()).isNotNull();
		assertThat(this.registry.find("unitrack.runs").gauge()).isNotNull();

		// ...and all of it surfaces in the Prometheus exposition — the only integration
		// needed.
		String scrape = this.prometheus.scrape();
		assertThat(scrape).contains("unitrack_ingest_seconds")
			.contains("unitrack_sse_subscribers")
			.contains("unitrack_runs");
	}

}
