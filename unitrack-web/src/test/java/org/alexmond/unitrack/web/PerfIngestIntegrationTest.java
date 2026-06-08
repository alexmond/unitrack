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
	void uploadWithoutAnyFileIsRejected() throws Exception {
		mockMvc().perform(multipart("/api/v1/ingest").param("project", "perf-none"))
			.andExpect(status().is4xxClientError());
	}

}
