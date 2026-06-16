package org.alexmond.unitrack.web.live;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end contract for the SSE spine: a run published while a real HTTP client is
 * connected to {@code /api/v1/events} arrives on that stream as a {@code data:} line.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SseStreamContractIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private LiveEventService liveEvents;

	@Test
	void aPublishedRunReachesAnOpenStream() throws Exception {
		List<String> lines = new CopyOnWriteArrayList<>();
		HttpClient client = HttpClient.newHttpClient();
		HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + "/api/v1/events"))
			.header("Accept", "text/event-stream")
			.GET()
			.build();
		Thread reader = new Thread(() -> {
			try {
				HttpResponse<Stream<String>> resp = client.send(req, HttpResponse.BodyHandlers.ofLines());
				resp.body().forEach(lines::add);
			}
			catch (Exception ignored) {
				// stream closed at test end
			}
		});
		reader.setDaemon(true);
		reader.start();

		// Wait until THIS stream has received its own `connected` event — that guarantees
		// our
		// emitter is registered (other tests in the shared context may hold streams too,
		// so a
		// global subscriber count can't tell us our own is ready).
		waitUntil(() -> lines.contains("data:ok"), 5_000);
		Project pub = new Project("contract-proj", null);
		pub.setVisibility(Visibility.PUBLIC);
		RunUpdate update = new RunUpdate(1L, 4242L, "main", "default", "PASSED", 1, 0, 0, 100.0, 80.0, 1_000L,
				"CONTRACTSHA", "2026-06-16T00:00:00Z");
		this.liveEvents.publish(pub, update);

		// The marker shows up on the stream as an SSE data line.
		waitUntil(() -> lines.stream().anyMatch((l) -> l.contains("CONTRACTSHA")), 5_000);
		assertThat(lines).anyMatch((l) -> l.startsWith("data:") && l.contains("CONTRACTSHA"));
	}

	private static void waitUntil(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			Thread.sleep(50);
		}
	}

}
