package org.alexmond.unitrack.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the uploader's reliability contract (#119) end to end: the real Spring-wired
 * command + client + retry policy, driven against an in-process mock ingest server. Exit
 * codes, retry, dry-run, token redaction and odd filenames are all asserted here.
 */
@SpringBootTest
class UploadContractTest {

	@Autowired
	private UnitrackCommand root;

	@Autowired
	private ApplicationContext context;

	@TempDir
	private Path dir;

	private HttpServer server;

	private Handler handler;

	private String baseUrl;

	private static final String JUNIT = "<?xml version=\"1.0\"?>"
			+ "<testsuite name=\"com.x.S\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\" time=\"0.01\">"
			+ "<testcase name=\"ok\" classname=\"com.x.S\" time=\"0.01\"/></testsuite>";

	@BeforeEach
	void startServer() throws IOException {
		this.handler = new Handler();
		this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		this.server.createContext("/api/v1/ingest", this.handler);
		this.server.start();
		this.baseUrl = "http://localhost:" + this.server.getAddress().getPort();
	}

	@AfterEach
	void stopServer() {
		this.server.stop(0);
	}

	private Path junitFile(String name) throws IOException {
		Path f = this.dir.resolve(name);
		Files.writeString(f, JUNIT);
		return f;
	}

	/**
	 * Runs the real command tree (Spring-resolved beans) and returns [exitCode, stdout].
	 */
	private Result run(String... args) {
		IFactory factory = new IFactory() {
			@Override
			public <K> K create(Class<K> cls) throws Exception {
				try {
					return UploadContractTest.this.context.getBean(cls);
				}
				catch (RuntimeException ex) {
					return CommandLine.defaultFactory().create(cls);
				}
			}
		};
		PrintStream realOut = System.out;
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int code;
		try {
			System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
			code = new CommandLine(this.root, factory).setCaseInsensitiveEnumValuesAllowed(true).execute(args);
		}
		finally {
			System.setOut(realOut);
		}
		return new Result(code, buffer.toString(StandardCharsets.UTF_8));
	}

	@Test
	void noMatchingFilesExitsUsage() throws IOException {
		Result r = run("upload", "--url", this.baseUrl, "--project", "p", "--commit", "c1", "--junit",
				this.dir.resolve("does-not-exist-*.xml").toString());
		assertThat(r.exit()).isEqualTo(ExitCodes.USAGE);
		assertThat(this.handler.count.get()).isZero();
	}

	@Test
	void happyPathUploadsAndPrintsRunId() throws IOException {
		Result r = run("upload", "--url", this.baseUrl, "--project", "p", "--commit", "c1", "--junit",
				junitFile("TEST.xml").toString());
		assertThat(r.exit()).isEqualTo(ExitCodes.OK);
		assertThat(r.out()).contains("run #1");
		assertThat(this.handler.count.get()).isEqualTo(1);
	}

	@Test
	void retriesTransientServerErrorThenSucceeds() throws IOException {
		this.handler.codes.add(503); // first attempt: retryable, then default 201
		Result r = run("upload", "--url", this.baseUrl, "--project", "p", "--commit", "c1", "--junit",
				junitFile("TEST.xml").toString());
		assertThat(r.exit()).isEqualTo(ExitCodes.OK);
		assertThat(this.handler.count.get()).isEqualTo(2); // retried exactly once
	}

	@Test
	void payloadTooLargeIsRejected() throws IOException {
		this.handler.codes.add(413);
		Result r = run("upload", "--url", this.baseUrl, "--project", "p", "--commit", "c1", "--junit",
				junitFile("TEST.xml").toString());
		assertThat(r.exit()).isEqualTo(ExitCodes.REJECTED);
	}

	@Test
	void tokenIsRedactedFromLogsButSentToServer() throws IOException {
		Result r = run("upload", "--url", this.baseUrl, "--token", "ut_SECRETXYZ", "--verbose", "--project", "p",
				"--commit", "c1", "--junit", junitFile("TEST.xml").toString());
		assertThat(r.exit()).isEqualTo(ExitCodes.OK);
		assertThat(r.out()).doesNotContain("ut_SECRETXYZ"); // never leaked to logs
		assertThat(this.handler.lastAuth).contains("ut_SECRETXYZ"); // but the request
																	// carries it
	}

	@Test
	void filenameWithSpacesIsUploaded() throws IOException {
		Result r = run("upload", "--url", this.baseUrl, "--project", "p", "--commit", "c1", "--junit",
				junitFile("TEST with space.xml").toString());
		assertThat(r.exit()).isEqualTo(ExitCodes.OK);
		assertThat(this.handler.count.get()).isEqualTo(1);
	}

	@Test
	void dryRunSendsNothing() throws IOException {
		Result r = run("upload", "--url", this.baseUrl, "--dry-run", "--project", "p", "--commit", "c1", "--junit",
				junitFile("TEST.xml").toString());
		assertThat(r.exit()).isEqualTo(ExitCodes.OK);
		assertThat(this.handler.count.get()).isZero();
	}

	@Test
	void sameRunKeyIsSentOnEachUpload() throws IOException {
		String file = junitFile("TEST.xml").toString();
		assertThat(run("upload", "--url", this.baseUrl, "--project", "p", "--commit", "c1", "--run-key", "k1",
				"--junit", file)
			.exit()).isEqualTo(ExitCodes.OK);
		assertThat(run("upload", "--url", this.baseUrl, "--project", "p", "--commit", "c1", "--run-key", "k1",
				"--junit", file)
			.exit()).isEqualTo(ExitCodes.OK);
		// Both carry the runKey; merging into one run is the server's job (tested there).
		assertThat(this.handler.bodies).hasSize(2).allMatch((b) -> b.contains("k1"));
	}

	/**
	 * Programmable mock ingest endpoint: counts requests, queues response codes, captures
	 * auth.
	 */
	private static final class Handler implements com.sun.net.httpserver.HttpHandler {

		private final AtomicInteger count = new AtomicInteger();

		private final ConcurrentLinkedQueue<Integer> codes = new ConcurrentLinkedQueue<>();

		private final List<String> bodies = new CopyOnWriteArrayList<>();

		private volatile String lastAuth;

		@Override
		public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
			this.count.incrementAndGet();
			this.lastAuth = exchange.getRequestHeaders().getFirst("Authorization");
			this.bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			Integer code = this.codes.poll();
			int status = (code != null) ? code : 201;
			byte[] body = (status == 201) ? "{\"runId\":1}".getBytes(StandardCharsets.UTF_8)
					: ("error " + status).getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, body.length);
			try (var os = exchange.getResponseBody()) {
				os.write(body);
			}
		}

	}

	private record Result(int exit, String out) {
	}

}
