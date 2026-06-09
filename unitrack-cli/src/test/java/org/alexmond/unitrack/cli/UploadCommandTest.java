package org.alexmond.unitrack.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class UploadCommandTest {

	private final UploadClient client = mock(UploadClient.class);

	private UploadCommand command() {
		UploadCommand c = new UploadCommand(this.client, new ReportResolver());
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

}
