package org.alexmond.unitrack.web;

import org.alexmond.unitrack.domain.IngestJob;
import org.alexmond.unitrack.domain.IngestStatus;
import org.alexmond.unitrack.repository.IngestJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every ingest is recorded as an {@link IngestJob} — PROCESSED on success, FAILED+reason
 * otherwise (#368).
 */
@SpringBootTest
class IngestJobRecordingIntegrationTest {

	private static final byte[] JUNIT = ("<?xml version=\"1.0\"?><testsuite name=\"S\" tests=\"1\" failures=\"0\" "
			+ "errors=\"0\" skipped=\"0\" time=\"0.01\"><testcase name=\"t\" classname=\"S\" time=\"0.01\"/></testsuite>")
		.getBytes();

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private IngestJobRepository jobs;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(this.context).build();
	}

	private IngestJob latestFor(String project) {
		return this.jobs.findByOrderByCreatedAtDesc(PageRequest.of(0, 50))
			.stream()
			.filter((j) -> project.equals(j.getProjectName()))
			.findFirst()
			.orElseThrow();
	}

	@Test
	void successfulUploadRecordsAProcessedJob() throws Exception {
		mockMvc()
			.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST-S.xml", "text/xml", JUNIT))
				.param("project", "ij-ok")
				.param("commit", "c1"))
			.andExpect(status().isCreated());

		IngestJob job = latestFor("ij-ok");
		assertThat(job.getStatus()).isEqualTo(IngestStatus.PROCESSED);
		assertThat(job.getKind()).isEqualTo("tests");
		assertThat(job.getRunId()).isNotNull();
		assertThat(job.getDurationMs()).isNotNull();
		assertThat(job.getSizeBytes()).isPositive();
	}

	@Test
	void malformedUploadRecordsAFailedJobWithReason() throws Exception {
		mockMvc()
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("junit", "broken.xml", "text/xml", "not a test report".getBytes()))
				.param("project", "ij-bad"))
			.andExpect(status().is4xxClientError());

		IngestJob job = latestFor("ij-bad");
		assertThat(job.getStatus()).isEqualTo(IngestStatus.FAILED);
		assertThat(job.getFailureReason()).isNotBlank();
		assertThat(job.getRunId()).isNull();
	}

	@Test
	void historyUiAndApiExposeRecordedJobs() throws Exception {
		mockMvc()
			.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST-S.xml", "text/xml", JUNIT))
				.param("project", "ij-hist"))
			.andExpect(status().isCreated());

		mockMvc().perform(get("/api/v1/ingest-jobs"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("ij-hist")));
		mockMvc().perform(get("/ingest"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Ingest history")));
	}

}
