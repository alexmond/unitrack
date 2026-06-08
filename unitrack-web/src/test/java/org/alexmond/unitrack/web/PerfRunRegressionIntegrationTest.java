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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PerfRunRegressionIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private static byte[] jtl(int elapsed, boolean firstFails) {
		return ("timeStamp,elapsed,label,success\n" + "1000," + elapsed + ",GET /a," + (firstFails ? "false" : "true")
				+ "\n" + "1100," + elapsed + ",GET /a,true\n" + "1200," + elapsed + ",GET /a,true\n")
			.getBytes();
	}

	private long ingest(MockMvc mvc, String commit, int elapsed, boolean fails) throws Exception {
		String body = mvc
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("perf", "results.jtl", "text/csv", jtl(elapsed, fails)))
				.param("project", "perfreg-web")
				.param("branch", "main")
				.param("commit", commit))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.perfRunId")).longValue();
	}

	@Test
	void regressionEndpointReturns422OnRegressionAnd200WhenWithinThresholds() throws Exception {
		MockMvc mvc = mockMvc();
		long baseline = ingest(mvc, "base", 100, false); // fast, clean
		long current = ingest(mvc, "slow", 500, true); // 5x latency + errors

		// Baseline: no prior run -> within thresholds -> 200.
		mvc.perform(get("/api/v1/perf-runs/{id}/regression", baseline))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.passed").value(true))
			.andExpect(jsonPath("$.baselineFound").value(false));

		// Current: regressed -> 422, with the failing rules in the body.
		mvc.perform(get("/api/v1/perf-runs/{id}/regression", current))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.passed").value(false))
			.andExpect(jsonPath("$.baselineRunId").value((int) baseline))
			.andExpect(jsonPath("$.rules[*].name").value(org.hamcrest.Matchers.hasItem("latency-p95")));
	}

	@Test
	void unknownPerfRunIsNotFound() throws Exception {
		mockMvc().perform(get("/api/v1/perf-runs/{id}/regression", 999999)).andExpect(status().isNotFound());
	}

}
