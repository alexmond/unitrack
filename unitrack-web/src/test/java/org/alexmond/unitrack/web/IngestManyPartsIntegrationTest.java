package org.alexmond.unitrack.web;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A real-container ingest test: one multipart request carrying many JUnit parts. Tomcat
 * 11 (Spring Boot 4) caps a request at 10 parts by default, so a normal suite — one part
 * per report file, often dozens — is rejected with "Maximum upload size exceeded" unless
 * {@code server.tomcat.max-part-count} is raised. {@code MockMvc} bypasses the connector
 * and can't catch this, so this runs on a real port. Guards against the Tomcat default
 * creeping back.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IngestManyPartsIntegrationTest {

	private static final int PARTS = 25;

	private static final String BOUNDARY = "----unitrackManyParts";

	@LocalServerPort
	private int port;

	@Test
	void acceptsRequestWithManyJunitParts() throws Exception {
		byte[] junit = getClass().getResourceAsStream("/samples/surefire-sample.xml").readAllBytes();

		ByteArrayOutputStream body = new ByteArrayOutputStream();
		// Well above Tomcat's default cap of 10 parts.
		for (int i = 0; i < PARTS; i++) {
			filePart(body, "junit", "TEST-Suite" + i + ".xml", junit);
		}
		fieldPart(body, "project", "many-parts");
		fieldPart(body, "branch", "main");
		fieldPart(body, "commit", "abcdef1234567890");
		body.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

		HttpResponse<String> resp = HttpClient.newHttpClient()
			.send(HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + "/api/v1/ingest"))
				.header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
				.build(), HttpResponse.BodyHandlers.ofString());

		assertThat(resp.statusCode()).isEqualTo(201);
		// surefire-sample.xml carries 4 tests; PARTS copies are merged into one run.
		assertThat(resp.body()).contains("\"total\":" + (PARTS * 4));
	}

	private static void filePart(ByteArrayOutputStream out, String name, String filename, byte[] content)
			throws Exception {
		out.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
		out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n")
			.getBytes(StandardCharsets.UTF_8));
		out.write("Content-Type: text/xml\r\n\r\n".getBytes(StandardCharsets.UTF_8));
		out.write(content);
		out.write("\r\n".getBytes(StandardCharsets.UTF_8));
	}

	private static void fieldPart(ByteArrayOutputStream out, String name, String value) throws Exception {
		out.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
		out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		out.write(value.getBytes(StandardCharsets.UTF_8));
		out.write("\r\n".getBytes(StandardCharsets.UTF_8));
	}

}
