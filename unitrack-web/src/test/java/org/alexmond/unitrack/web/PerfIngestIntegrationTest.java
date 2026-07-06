package org.alexmond.unitrack.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PerfIngestIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private static final byte[] JTL = ("timeStamp,elapsed,label,success\n" + "1000,100,GET /a,true\n"
			+ "1100,200,GET /a,true\n" + "1200,300,GET /a,false\n")
		.getBytes();

	private static final byte[] JUNIT = ("<?xml version=\"1.0\"?><testsuite name=\"S\" tests=\"1\" failures=\"0\" "
			+ "errors=\"0\" skipped=\"0\" time=\"0.01\"><testcase name=\"t\" classname=\"S\" time=\"0.01\"/></testsuite>")
		.getBytes();

	@Test
	void perfOnlyUploadCreatesPerfRun() throws Exception {
		mockMvc()
			.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("perf", "results.jtl", "text/csv", JTL))
				.param("project", "perf-only")
				.param("branch", "main")
				.param("commit", "c1"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.perfRunId").isNumber())
			.andExpect(jsonPath("$.perfP95Ms").value(300.0))
			.andExpect(jsonPath("$.runId").doesNotExist());
	}

	@Test
	void combinedTestPlusPerfUploadCreatesBoth() throws Exception {
		mockMvc()
			.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST-S.xml", "text/xml", JUNIT))
				.file(new MockMultipartFile("perf", "results.jtl", "text/csv", JTL))
				.param("project", "perf-combined")
				.param("commit", "c1"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.runId").isNumber())
			.andExpect(jsonPath("$.perfRunId").isNumber());
	}

	@Test
	void jmhJsonUploadFromGotmpl4jCreatesPerfRun() throws Exception {
		// Real `jmh -rf json` output from the gotmpl4j benchmark suite (the exact bytes
		// its CI
		// uploads). Exercises the JMH path end-to-end: avgt time-per-op, a no-param
		// benchmark,
		// and a @Param'd one (TableBenchmark.gotmpl4jRender[n=...]) all in one report.
		byte[] jmh = getClass().getResourceAsStream("/perf/gotmpl4j-jmh.json").readAllBytes();
		mockMvc()
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("perf", "jmh-result.json", "application/json", jmh))
				.param("project", "gotmpl4j-jmh")
				.param("branch", "main")
				.param("commit", "c1"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.perfRunId").isNumber())
			.andExpect(jsonPath("$.perfP95Ms").isNumber());
	}

	@Test
	void uploadWithoutAnyFileIsRejected() throws Exception {
		mockMvc().perform(multipart("/api/v1/ingest").param("project", "perf-none"))
			.andExpect(status().is4xxClientError());
	}

}
