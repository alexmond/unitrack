package org.alexmond.unitrack.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PerfRunDetailIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private static byte[] jtl(int elapsed, boolean firstFails) {
		return ("timeStamp,elapsed,label,success\n" + "1000," + elapsed + ",GET /a," + (firstFails ? "false" : "true")
				+ "\n" + "1100," + elapsed + ",GET /a,true\n" + "1200,80,GET /b,true\n")
			.getBytes();
	}

	private long ingest(MockMvc mvc, String commit, int elapsed, boolean fails) throws Exception {
		String body = mvc
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("perf", "results.jtl", "text/csv", jtl(elapsed, fails)))
				.param("project", "perfdetail-web")
				.param("branch", "main")
				.param("commit", commit))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.perfRunId")).longValue();
	}

	@Test
	void detailApiReturnsPerLabelRowsRegressionAndBaselineDelta() throws Exception {
		MockMvc mvc = mockMvc();
		long baseline = ingest(mvc, "base", 100, false);
		long current = ingest(mvc, "slow", 300, true);

		mvc.perform(get("/api/v1/perf-runs/{id}", current))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.runId").value((int) current))
			.andExpect(jsonPath("$.projectName").value("perfdetail-web"))
			.andExpect(jsonPath("$.labels[*].label").value(org.hamcrest.Matchers.hasItem("GET /a")))
			.andExpect(jsonPath("$.regression.baselineRunId").value((int) baseline))
			.andExpect(jsonPath("$.regression.passed").value(false));
	}

	@Test
	void detailPageRendersPerLabelTable() throws Exception {
		MockMvc mvc = mockMvc();
		long run = ingest(mvc, "page", 120, false);

		mvc.perform(get("/perf-runs/{id}", run))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Per-label breakdown")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("GET /a")));
	}

	@Test
	void unknownPerfRunIsNotFound() throws Exception {
		mockMvc().perform(get("/api/v1/perf-runs/{id}", 999999)).andExpect(status().isNotFound());
	}

}
