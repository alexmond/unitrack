package org.alexmond.unitrack.web.ops;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Observability #3: /actuator/health is UP and includes the custom live-stream component
 * with its subscriber detail; /actuator/info exposes the app metadata.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthInfoIntegrationTest {

	@LocalServerPort
	private int port;

	private String get(String path) throws Exception {
		HttpClient client = HttpClient.newHttpClient();
		HttpResponse<String> resp = client.send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + path)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(resp.statusCode()).isEqualTo(200);
		return resp.body();
	}

	@Test
	void healthIsUpWithTheLiveStreamComponent() throws Exception {
		String body = get("/actuator/health");
		assertThat(body).contains("\"status\":\"UP\"").contains("liveStream").contains("subscribers");
	}

	@Test
	void infoExposesAppMetadata() throws Exception {
		assertThat(get("/actuator/info")).contains("UniTrack");
	}

	@Autowired
	private LiveStreamHealthIndicator indicator;

	@Test
	void indicatorReportsSubscriberCount() {
		assertThat(this.indicator.health().getStatus().getCode()).isEqualTo("UP");
		assertThat(this.indicator.health().getDetails()).containsKey("subscribers");
	}

}
