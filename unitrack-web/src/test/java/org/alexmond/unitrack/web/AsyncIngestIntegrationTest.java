package org.alexmond.unitrack.web;

import java.time.Duration;
import java.util.List;

import org.alexmond.unitrack.domain.IngestJob;
import org.alexmond.unitrack.domain.IngestStatus;
import org.alexmond.unitrack.web.ingest.IngestJobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import org.alexmond.unitrack.repository.IngestJobRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Async ingest (#368): {@code ?async=true} accepts (202), a bounded worker processes
 * off-thread, the job is pollable, and stuck jobs are recovered on restart.
 */
@SpringBootTest
class AsyncIngestIntegrationTest {

	private static final byte[] JUNIT = ("<?xml version=\"1.0\"?><testsuite name=\"S\" tests=\"1\" failures=\"0\" "
			+ "errors=\"0\" skipped=\"0\" time=\"0.01\"><testcase name=\"t\" classname=\"S\" time=\"0.01\"/></testsuite>")
		.getBytes();

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private IngestJobService ingestJobs;

	@Autowired
	private IngestJobRepository jobRepo;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(this.context).build();
	}

	private IngestJob latestFor(String project) {
		return this.jobRepo.findByOrderByCreatedAtDesc(PageRequest.of(0, 100))
			.stream()
			.filter((j) -> project.equals(j.getProjectName()))
			.findFirst()
			.orElseThrow();
	}

	@Test
	void asyncUploadIsAcceptedThenProcessedOffThread() throws Exception {
		mockMvc()
			.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST-S.xml", "text/xml", JUNIT))
				.param("project", "async-ok")
				.param("commit", "c1")
				.param("async", "true"))
			.andExpect(status().isAccepted())
			.andExpect(content().string(containsString("\"jobId\"")))
			.andExpect(content().string(containsString("QUEUED")));

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
			IngestJob job = latestFor("async-ok");
			assertThat(job.getStatus()).isEqualTo(IngestStatus.PROCESSED);
			assertThat(job.getRunId()).isNotNull();
			assertThat(job.getKind()).isEqualTo("tests");
		});
	}

	@Test
	void asyncMalformedUploadEndsFailedWithReason() throws Exception {
		mockMvc()
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("junit", "broken.xml", "text/xml", "not a test report".getBytes()))
				.param("project", "async-bad")
				.param("async", "true"))
			.andExpect(status().isAccepted());

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
			IngestJob job = latestFor("async-bad");
			assertThat(job.getStatus()).isEqualTo(IngestStatus.FAILED);
			assertThat(job.getFailureReason()).isNotBlank();
			assertThat(job.getRunId()).isNull();
		});
	}

	@Test
	void jobByIdIsVisibleToAdminButNotAnonymous() throws Exception {
		mockMvc()
			.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST-S.xml", "text/xml", JUNIT))
				.param("project", "async-poll")
				.param("async", "true"))
			.andExpect(status().isAccepted());
		await().atMost(Duration.ofSeconds(20))
			.until(() -> latestFor("async-poll").getStatus() == IngestStatus.PROCESSED);
		long id = latestFor("async-poll").getId();

		// Anonymous: the job is hidden (404, no existence leak).
		SecurityContextHolder.clearContext();
		mockMvc().perform(get("/api/v1/ingest-jobs/{id}", id)).andExpect(status().isNotFound());

		// Admin: the job (and its outcome) is visible.
		try {
			SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken("admin", "x",
						List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
			mockMvc().perform(get("/api/v1/ingest-jobs/{id}", id))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("PROCESSED")));
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	@Test
	void recoverStuckFailsInFlightJobs() {
		Long id = this.ingestJobs.enqueue("async-stuck", "main", "c1", "tests", 10, null);
		this.ingestJobs.markProcessing(id);

		assertThat(this.ingestJobs.recoverStuck()).isPositive();

		IngestJob job = this.ingestJobs.find(id).orElseThrow();
		assertThat(job.getStatus()).isEqualTo(IngestStatus.FAILED);
		assertThat(job.getFailureReason()).contains("restart");
		assertThat(job.getFinishedAt()).isNotNull();
	}

}
