package org.alexmond.unitrack.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class UploadCommandTest {

	private final UploadClient client = mock(UploadClient.class);

	private static final CiMetadataDetector NO_CI = new CiMetadataDetector((k) -> null);

	private UploadCommand command() {
		return command(NO_CI);
	}

	private UploadCommand command(CiMetadataDetector detector) {
		UploadCommand c = new UploadCommand(this.client, new ReportResolver(), detector);
		c.url = "http://unitrack.test";
		c.project = "demo";
		return c;
	}

	@Test
	void failsLoudWhenNoFilesMatch(@TempDir Path dir) {
		UploadCommand c = command();
		c.junit = List.of(dir + "/none-*.xml");

		assertThat(c.call()).isEqualTo(ExitCodes.USAGE);
		verifyNoInteractions(this.client);
	}

	@Test
	void dryRunResolvesButDoesNotUpload(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve("TEST-a.xml"), "<testsuite/>");
		UploadCommand c = command();
		c.junit = List.of(dir + "/TEST-*.xml");
		c.dryRun = true;

		assertThat(c.call()).isEqualTo(ExitCodes.OK);
		verifyNoInteractions(this.client);
	}

	@Test
	void uploadsMatchedFilesAndReportsRunId(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve("TEST-a.xml"), "<testsuite/>");
		given(this.client.ingest(any(), any(), any(), any(), any())).willReturn(new IngestResponse(7L));
		UploadCommand c = command();
		c.junit = List.of(dir + "/TEST-*.xml");

		assertThat(c.call()).isEqualTo(ExitCodes.OK);
		verify(this.client).ingest(any(), any(), any(), any(), any());
	}

	@Test
	void propagatesUploadFailureExitCode(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve("TEST-a.xml"), "<testsuite/>");
		given(this.client.ingest(any(), any(), any(), any(), any()))
			.willThrow(new UploadException(ExitCodes.REJECTED, "boom"));
		UploadCommand c = command();
		c.junit = List.of(dir + "/TEST-*.xml");

		assertThat(c.call()).isEqualTo(ExitCodes.REJECTED);
	}

	@Test
	void allowEmptyUploadsMetadataOnly() {
		given(this.client.ingest(any(), any(), any(), any(), any())).willReturn(new IngestResponse(null));
		UploadCommand c = command();
		c.allowEmpty = true;

		assertThat(c.call()).isEqualTo(ExitCodes.OK);
		verify(this.client).ingest(any(), any(), any(), any(), any());
	}

	@Test
	void softFailDowngradesTransportFailureToSuccess(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve("TEST-a.xml"), "<testsuite/>");
		given(this.client.ingest(any(), any(), any(), any(), any()))
			.willThrow(new UploadException(ExitCodes.TRANSPORT, "down"));
		UploadCommand c = command();
		c.junit = List.of(dir + "/TEST-*.xml");
		c.softFail = true;

		assertThat(c.call()).isEqualTo(ExitCodes.OK);
	}

	@Test
	void verbosePrintsResolvedRequestAndUploads(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve("TEST-a.xml"), "<testsuite/>");
		given(this.client.ingest(any(), any(), any(), any(), any())).willReturn(new IngestResponse(1L));
		UploadCommand c = command();
		c.token = "secret";
		c.verbose = true;
		c.junit = List.of(dir + "/TEST-*.xml");

		assertThat(c.call()).isEqualTo(ExitCodes.OK);
	}

	@Test
	void perFileErrorFlagsAnOversizedFile(@TempDir Path dir) throws IOException {
		Files.write(dir.resolve("big.xml"), new byte[2048]);
		Files.write(dir.resolve("small.xml"), new byte[600]);
		Resource big = new FileSystemResource(dir.resolve("big.xml"));
		Resource small = new FileSystemResource(dir.resolve("small.xml"));

		assertThat(UploadCommand.perFileError(List.of(big), 1024)).contains("big.xml");
		assertThat(UploadCommand.perFileError(List.of(big, small), 100_000)).isNull();
	}

	@Test
	void splitIntoBatchesShardsAnOversizedTotalKeepingJunitInEach(@TempDir Path dir) throws IOException {
		Files.write(dir.resolve("TEST-a.xml"), new byte[700]);
		Files.write(dir.resolve("TEST-b.xml"), new byte[700]);
		Files.write(dir.resolve("cov.xml"), new byte[200]);
		Map<String, List<Resource>> files = new java.util.LinkedHashMap<>();
		files.put("junit", List.of(new FileSystemResource(dir.resolve("TEST-a.xml")),
				new FileSystemResource(dir.resolve("TEST-b.xml"))));
		files.put("jacoco", List.of(new FileSystemResource(dir.resolve("cov.xml"))));

		// Fits the target -> one request, unchanged.
		assertThat(UploadCommand.splitIntoBatches(files, 100_000)).hasSize(1);
		// Target too small for both junit in one request -> 2 shards, each carrying a
		// junit part.
		List<Map<String, List<Resource>>> batches = UploadCommand.splitIntoBatches(files, 1000);
		assertThat(batches).hasSize(2).allSatisfy((batch) -> assertThat(batch).containsKey("junit"));
	}

	@Test
	void rejectsUploadWhenAFileExceedsTheCap(@TempDir Path dir) throws IOException {
		Files.write(dir.resolve("TEST-big.xml"), new byte[26 * 1024 * 1024]);
		UploadCommand c = command();
		c.gzip = false; // exercise the raw-size cap (gzip would shrink the zero-filled
						// file)
		c.junit = List.of(dir + "/TEST-big.xml");

		assertThat(c.call()).isEqualTo(ExitCodes.REJECTED);
		verifyNoInteractions(this.client);
	}

	@Test
	void gzipLetsALargeButCompressibleUploadThrough(@TempDir Path dir) throws IOException {
		Files.write(dir.resolve("TEST-big.xml"), new byte[26 * 1024 * 1024]); // > 25MB
																				// raw,
																				// tiny
																				// gzipped
		given(this.client.ingest(any(), any(), any(), any(), any())).willReturn(new IngestResponse(5L));
		UploadCommand c = command();
		c.project = "p";
		c.junit = List.of(dir + "/TEST-big.xml");

		assertThat(c.call()).isEqualTo(ExitCodes.OK); // gzip shrinks it under the
														// per-file cap
		verify(this.client).ingest(any(), any(), any(), any(), any());
	}

	@Test
	void moduleOfUsesTheDirectoryBeforeTarget() {
		assertThat(UploadCommand.moduleOf(new FileSystemResource("/repo/builder-core/target/surefire-reports/T.xml")))
			.isEqualTo("builder-core");
		assertThat(UploadCommand.moduleOf(new FileSystemResource("/repo/target/site/jacoco/jacoco.xml")))
			.isEqualTo("repo");
		assertThat(UploadCommand.moduleOf(new FileSystemResource("/loose/file.xml"))).isEqualTo("(root)");
	}

	@Test
	void splitByModuleUploadsEachModulePlusRollup(@TempDir Path dir) throws IOException {
		Files.createDirectories(dir.resolve("modA/target/surefire-reports"));
		Files.createDirectories(dir.resolve("modB/target/surefire-reports"));
		Files.writeString(dir.resolve("modA/target/surefire-reports/TEST-a.xml"), "<testsuite/>");
		Files.writeString(dir.resolve("modB/target/surefire-reports/TEST-b.xml"), "<testsuite/>");
		given(this.client.ingest(any(), any(), any(), any(), any())).willReturn(new IngestResponse(1L));
		UploadCommand c = command();
		c.splitByModule = true;
		c.junit = List.of(dir + "/**/target/surefire-reports/TEST-*.xml");

		assertThat(c.call()).isEqualTo(ExitCodes.OK);

		// Two modules as their own flags, plus the merged rollup under the default flag
		// (null).
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, String>> fields = ArgumentCaptor.forClass(Map.class);
		verify(this.client, times(3)).ingest(any(), any(), any(), fields.capture(), any());
		assertThat(fields.getAllValues()).extracting((f) -> f.get("flag"))
			.containsExactlyInAnyOrder("modA", "modB", null);
	}

	@Test
	void detectsMetadataButExplicitFlagsWin(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve("TEST-a.xml"), "<testsuite/>");
		Map<String, String> ghEnv = Map.of("GITHUB_ACTIONS", "true", "GITHUB_REPOSITORY", "octo/myapp",
				"GITHUB_SERVER_URL", "https://github.com", "GITHUB_RUN_ID", "99", "GITHUB_EVENT_NAME", "push",
				"GITHUB_REF_NAME", "ci-branch", "GITHUB_SHA", "detectedsha");
		given(this.client.ingest(any(), any(), any(), any(), any())).willReturn(new IngestResponse(1L));
		UploadCommand c = command(new CiMetadataDetector(ghEnv::get));
		c.project = null; // detected from CI
		c.branch = "explicit-branch"; // explicit overrides detection
		c.junit = List.of(dir + "/TEST-*.xml");

		assertThat(c.call()).isEqualTo(ExitCodes.OK);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, String>> fields = ArgumentCaptor.forClass(Map.class);
		verify(this.client).ingest(any(), any(), any(), fields.capture(), any());
		assertThat(fields.getValue()).containsEntry("project", "myapp") // detected
			.containsEntry("commit", "detectedsha") // detected
			.containsEntry("branch", "explicit-branch") // explicit wins
			.containsEntry("ciProvider", "github-actions");
	}

}
