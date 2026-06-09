package org.alexmond.unitrack.cli;

import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import java.time.Duration;

import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.HttpMethod;

class UploadClientTest {

	private RestClient.Builder builder;

	private MockRestServiceServer server;

	private UploadClient client;

	private UploadClient newClient() {
		this.builder = RestClient.builder();
		this.server = MockRestServiceServer.bindTo(this.builder).build();
		RetryPolicy policy = RetryPolicy.builder()
			.maxRetries(2)
			.delay(Duration.ofMillis(1))
			.includes(RetryableUploadException.class)
			.build();
		this.client = new UploadClient(this.builder, new RetryTemplate(policy));
		return this.client;
	}

	@Test
	void ingestParsesRunIdFromResponse() {
		newClient();
		this.server.expect(requestTo("http://unitrack.test/api/v1/ingest"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("{\"runId\":42,\"project\":\"demo\"}", MediaType.APPLICATION_JSON));

		IngestResponse r = this.client.ingest("http://unitrack.test", "tok", Map.of("project", "demo"), Map.of());

		assertThat(r.runId()).isEqualTo(42L);
		this.server.verify();
	}

	@Test
	void ingestMaps4xxToRejected() {
		newClient();
		this.server.expect(requestTo("http://unitrack.test/api/v1/ingest"))
			.andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

		assertThatThrownBy(
				() -> this.client.ingest("http://unitrack.test", null, Map.of("project", "demo"), emptyFiles()))
			.isInstanceOfSatisfying(UploadException.class,
					(ex) -> assertThat(ex.exitCode()).isEqualTo(ExitCodes.REJECTED));
	}

	@Test
	void ingestMaps5xxToTransport() {
		newClient();
		this.server.expect(requestTo("http://unitrack.test/api/v1/ingest"))
			.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		assertThatThrownBy(() -> this.client.ingest("http://unitrack.test", null, Map.of("project", "demo"), Map.of()))
			.isInstanceOfSatisfying(UploadException.class,
					(ex) -> assertThat(ex.exitCode()).isEqualTo(ExitCodes.TRANSPORT));
	}

	@Test
	void retriesTransient5xxThenSucceeds() {
		newClient();
		this.server.expect(requestTo("http://unitrack.test/api/v1/ingest"))
			.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		this.server.expect(requestTo("http://unitrack.test/api/v1/ingest"))
			.andRespond(withSuccess("{\"runId\":7}", MediaType.APPLICATION_JSON));

		IngestResponse r = this.client.ingest("http://unitrack.test", null, Map.of("project", "demo"), Map.of());

		assertThat(r.runId()).isEqualTo(7L);
		this.server.verify();
	}

	@Test
	void gateReturnsVerdictForMatchingRun() {
		newClient();
		this.server.expect(requestTo(Matchers.containsString("/api/v1/gate")))
			.andExpect(queryParam("project", "demo"))
			.andExpect(queryParam("commit", "abc123"))
			.andRespond(withSuccess("{\"passed\":false,\"status\":\"FAILED\"}", MediaType.APPLICATION_JSON));

		GateResponse g = this.client.gate("http://unitrack.test", null, "demo", "abc123", null, null);

		assertThat(g.found()).isTrue();
		assertThat(g.passed()).isFalse();
		assertThat(g.status()).isEqualTo("FAILED");
	}

	@Test
	void gateReturnsNotFoundOn404() {
		newClient();
		this.server.expect(requestTo(Matchers.containsString("/api/v1/gate")))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));

		GateResponse g = this.client.gate("http://unitrack.test", null, "demo", "nope", null, null);

		assertThat(g.found()).isFalse();
	}

	private static Map<String, List<org.springframework.core.io.Resource>> emptyFiles() {
		return Map.of();
	}

}
