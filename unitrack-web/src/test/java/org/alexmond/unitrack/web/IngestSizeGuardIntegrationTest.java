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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The decompressed-size guard (#369) rejects an oversized report before it can OOM the
 * parser, and the rejection is recorded as a FAILED ingest job (#368). The report cap is
 * pinned tiny here so a small fixture trips it.
 */
@SpringBootTest(properties = "unitrack.ingest.max-report-bytes=512B")
class IngestSizeGuardIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private IngestJobRepository jobs;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(this.context).build();
	}

	/** A valid-but-large JUnit document (well over 512 bytes). */
	private static byte[] bigJunit() {
		StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?><testsuite name=\"S\" tests=\"200\">");
		for (int i = 0; i < 200; i++) {
			sb.append("<testcase name=\"t").append(i).append("\" classname=\"C\" time=\"0.01\"/>");
		}
		sb.append("</testsuite>");
		return sb.toString().getBytes();
	}

	@Test
	void oversizedReportIsRejectedAndRecordedFailed() throws Exception {
		assertThat(bigJunit().length).isGreaterThan(512);

		mockMvc()
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("junit", "TEST-big.xml", "text/xml", bigJunit()))
				.param("project", "guard-big")
				.param("commit", "c1"))
			.andExpect(status().is4xxClientError());

		IngestJob job = this.jobs.findByOrderByCreatedAtDesc(PageRequest.of(0, 50))
			.stream()
			.filter((j) -> "guard-big".equals(j.getProjectName()))
			.findFirst()
			.orElseThrow();
		assertThat(job.getStatus()).isEqualTo(IngestStatus.FAILED);
		assertThat(job.getFailureReason()).containsIgnoringCase("size limit");
		assertThat(job.getRunId()).isNull();
	}

}
