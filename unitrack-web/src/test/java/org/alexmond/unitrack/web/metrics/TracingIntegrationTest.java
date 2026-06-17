package org.alexmond.unitrack.web.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationView;
import java.util.List;
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
 * Observability #4: the ingest Observation and its post-ingest child both record, and the
 * child (unitrack.report) nests under the parent (unitrack.ingest) via an explicitly
 * passed parent.
 */
@SpringBootTest
class TracingIntegrationTest {

	private static final byte[] JUNIT = ("<?xml version=\"1.0\"?>"
			+ "<testsuite name=\"com.x.S\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\" time=\"0.01\">"
			+ "<testcase name=\"ok\" classname=\"com.x.S\" time=\"0.01\"/></testsuite>")
		.getBytes();

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private ObservationRegistry observationRegistry;

	@Test
	void ingestEmitsParentAndNestedChildObservations() throws Exception {
		RecordingHandler handler = new RecordingHandler();
		this.observationRegistry.observationConfig().observationHandler(handler);

		MockMvc mvc = MockMvcBuilders.webAppContextSetup(this.context).build();
		mvc.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST.xml", "text/xml", JUNIT))
			.param("project", "tracing-demo")
			.param("commit", "c1")).andExpect(status().isCreated());

		// Both observations were recorded...
		assertThat(handler.started).contains("unitrack.ingest", "unitrack.report");
		// ...and the child nests under the parent.
		assertThat(handler.parentName).containsEntry("unitrack.report", "unitrack.ingest");
	}

	/** Records the names of unitrack.* observations and each one's parent name. */
	private static final class RecordingHandler implements ObservationHandler<Observation.Context> {

		private final List<String> started = new CopyOnWriteArrayList<>();

		private final Map<String, String> parentName = new ConcurrentHashMap<>();

		@Override
		public boolean supportsContext(Observation.Context context) {
			return context != null && context.getName() != null && context.getName().startsWith("unitrack.");
		}

		@Override
		public void onStart(Observation.Context context) {
			this.started.add(context.getName());
			ObservationView parent = context.getParentObservation();
			if (parent != null) {
				this.parentName.put(context.getName(), parent.getContextView().getName());
			}
		}

	}

}
