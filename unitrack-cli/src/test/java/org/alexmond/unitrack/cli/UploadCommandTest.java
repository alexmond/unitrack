package org.alexmond.unitrack.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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
		given(this.client.ingest(any(), any(), any(), any())).willReturn(new IngestResponse(7L));
		UploadCommand c = command();
		c.junit = List.of(dir + "/TEST-*.xml");

		assertThat(c.call()).isEqualTo(ExitCodes.OK);
		verify(this.client).ingest(any(), any(), any(), any());
	}

	@Test
	void propagatesUploadFailureExitCode(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve("TEST-a.xml"), "<testsuite/>");
		given(this.client.ingest(any(), any(), any(), any()))
			.willThrow(new UploadException(ExitCodes.REJECTED, "boom"));
		UploadCommand c = command();
		c.junit = List.of(dir + "/TEST-*.xml");

		assertThat(c.call()).isEqualTo(ExitCodes.REJECTED);
	}

	@Test
	void allowEmptyUploadsMetadataOnly() {
		given(this.client.ingest(any(), any(), any(), any())).willReturn(new IngestResponse(null));
		UploadCommand c = command();
		c.allowEmpty = true;

		assertThat(c.call()).isEqualTo(ExitCodes.OK);
		verify(this.client).ingest(any(), any(), any(), any());
	}

	@Test
	void detectsMetadataButExplicitFlagsWin(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve("TEST-a.xml"), "<testsuite/>");
		Map<String, String> ghEnv = Map.of("GITHUB_ACTIONS", "true", "GITHUB_REPOSITORY", "octo/myapp",
				"GITHUB_SERVER_URL", "https://github.com", "GITHUB_RUN_ID", "99", "GITHUB_EVENT_NAME", "push",
				"GITHUB_REF_NAME", "ci-branch", "GITHUB_SHA", "detectedsha");
		given(this.client.ingest(any(), any(), any(), any())).willReturn(new IngestResponse(1L));
		UploadCommand c = command(new CiMetadataDetector(ghEnv::get));
		c.project = null; // detected from CI
		c.branch = "explicit-branch"; // explicit overrides detection
		c.junit = List.of(dir + "/TEST-*.xml");

		assertThat(c.call()).isEqualTo(ExitCodes.OK);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, String>> fields = ArgumentCaptor.forClass(Map.class);
		verify(this.client).ingest(any(), any(), fields.capture(), any());
		assertThat(fields.getValue()).containsEntry("project", "myapp") // detected
			.containsEntry("commit", "detectedsha") // detected
			.containsEntry("branch", "explicit-branch") // explicit wins
			.containsEntry("ciProvider", "github-actions");
	}

}
