package org.alexmond.unitrack.maven;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The upload goal discovers the module's reports and pushes them through the reused CLI
 * engine (verified against an in-process mock ingest server).
 */
class UploadMojoTest {

	private static final String JUNIT = "<?xml version=\"1.0\"?>"
			+ "<testsuite name=\"com.x.S\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\" time=\"0.01\">"
			+ "<testcase name=\"ok\" classname=\"com.x.S\" time=\"0.01\"/></testsuite>";

	@TempDir
	private Path projectDir;

	private HttpServer server;

	private final AtomicInteger requests = new AtomicInteger();

	private String baseUrl;

	@BeforeEach
	void startServer() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		this.server.createContext("/api/v1/ingest", new IngestHandler());
		this.server.start();
		this.baseUrl = "http://localhost:" + this.server.getAddress().getPort();
	}

	@AfterEach
	void stopServer() {
		this.server.stop(0);
	}

	private UploadMojo mojo() throws IOException {
		Path reports = this.projectDir.resolve("target/surefire-reports");
		Files.createDirectories(reports);
		Files.writeString(reports.resolve("TEST.xml"), JUNIT);
		MavenProject project = new MavenProject();
		project.setArtifactId("demo");
		project.setFile(this.projectDir.resolve("pom.xml").toFile());
		UploadMojo mojo = new UploadMojo();
		mojo.project = project;
		mojo.url = this.baseUrl;
		mojo.token = "ut_token";
		return mojo;
	}

	@Test
	void uploadsDiscoveredReports() throws Exception {
		UploadMojo mojo = mojo();
		assertThatCode(mojo::execute).doesNotThrowAnyException();
		assertThat(this.requests.get()).isEqualTo(1);
	}

	@Test
	void skipSendsNothing() throws Exception {
		UploadMojo mojo = mojo();
		mojo.skip = true;
		mojo.execute();
		assertThat(this.requests.get()).isZero();
	}

	private final class IngestHandler implements HttpHandler {

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			UploadMojoTest.this.requests.incrementAndGet();
			exchange.getRequestBody().readAllBytes();
			byte[] body = "{\"runId\":1}".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(201, body.length);
			try (var os = exchange.getResponseBody()) {
				os.write(body);
			}
		}

	}

}
