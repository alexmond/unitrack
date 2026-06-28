package org.alexmond.unitrack.web;

import java.util.List;

import org.alexmond.unitrack.domain.PerfRun;
import org.alexmond.unitrack.repository.PerfRunRepository;
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
 * One upload can carry several perf tests, each becoming its own flag-scoped series
 * (#380); previously only the first perf file was stored.
 */
@SpringBootTest
class MultiPerfIngestIntegrationTest {

	private static byte[] jtl(String label) {
		return ("timeStamp,elapsed,label,success\n1000,100," + label + ",true\n1100,120," + label + ",true\n")
			.getBytes();
	}

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private PerfRunRepository perfRuns;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(this.context).build();
	}

	@Test
	void uploadingMultiplePerfFilesRecordsOneSeriesEach() throws Exception {
		mockMvc()
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("perf", "api.jtl", "text/csv", jtl("GET /api")))
				.file(new MockMultipartFile("perf", "checkout.jtl", "text/csv", jtl("POST /checkout")))
				.param("project", "multiperf")
				.param("commit", "c1")
				.param("perfFlag", "api")
				.param("perfFlag", "checkout"))
			.andExpect(status().isCreated());

		List<PerfRun> runs = this.perfRuns.findByProjectIdOrderByCreatedAtDesc(projectIdFor("multiperf"),
				PageRequest.ofSize(10));
		assertThat(runs).hasSize(2);
		assertThat(runs).extracting(PerfRun::getFlag).containsExactlyInAnyOrder("api", "checkout");
	}

	@Test
	void perfFlagDefaultsToTheFilenameWhenNotGiven() throws Exception {
		mockMvc()
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("perf", "load-smoke.jtl", "text/csv", jtl("GET /")))
				.file(new MockMultipartFile("perf", "stress.jtl", "text/csv", jtl("GET /")))
				.param("project", "multiperf-fn"))
			.andExpect(status().isCreated());

		assertThat(this.perfRuns.findDistinctFlagsByProjectId(projectIdFor("multiperf-fn")))
			.containsExactlyInAnyOrder("load-smoke", "stress");
	}

	private Long projectIdFor(String project) {
		return this.perfRuns.findAll()
			.stream()
			.filter((r) -> project.equals(r.getProject().getName()))
			.map((r) -> r.getProject().getId())
			.findFirst()
			.orElseThrow();
	}

}
