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
class PerfRegressionIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	/** Two cases a/b with the given per-test times (seconds). */
	private static byte[] junit(double aSec, double bSec) {
		return ("<?xml version=\"1.0\"?><testsuite name=\"com.x.G\" tests=\"2\" failures=\"0\" errors=\"0\" "
				+ "skipped=\"0\" time=\"" + (aSec + bSec) + "\">" + "<testcase name=\"a\" classname=\"com.x.G\" time=\""
				+ aSec + "\"/>" + "<testcase name=\"b\" classname=\"com.x.G\" time=\"" + bSec + "\"/></testsuite>")
			.getBytes();
	}

	private long ingest(MockMvc mvc, String project, String commit, double a, double b) throws Exception {
		String content = mvc
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("junit", "TEST-G.xml", "text/xml", junit(a, b)))
				.param("project", project)
				.param("branch", "main")
				.param("commit", commit))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(content, "$.runId")).longValue();
	}

	@Test
	void flagsTestsThatGotSlowerVsBaseline() throws Exception {
		MockMvc mvc = mockMvc();
		long baseline = ingest(mvc, "perfreg-demo", "base", 0.10, 0.10); // a=100ms,
																			// b=100ms
		long current = ingest(mvc, "perfreg-demo", "slow", 0.30, 0.105); // a 100->300
																			// (regressed),
																			// b
		// ~unchanged

		mvc.perform(get("/api/v1/runs/{id}/perf-regression", current))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.baselineFound").value(true))
			.andExpect(jsonPath("$.baselineRunId").value((int) baseline))
			.andExpect(jsonPath("$.slower.length()").value(1))
			.andExpect(jsonPath("$.slower[0].name").value("a"))
			.andExpect(jsonPath("$.slower[0].baselineMs").value(100))
			.andExpect(jsonPath("$.slower[0].currentMs").value(300))
			.andExpect(jsonPath("$.slower[0].deltaMs").value(200));
	}

	@Test
	void noBaselineForFirstRun() throws Exception {
		MockMvc mvc = mockMvc();
		long first = ingest(mvc, "perfreg-first", "first", 0.50, 0.50);
		mvc.perform(get("/api/v1/runs/{id}/perf-regression", first))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.baselineFound").value(false))
			.andExpect(jsonPath("$.slower.length()").value(0));
	}

	@Test
	void unknownRunIsNotFound() throws Exception {
		mockMvc().perform(get("/api/v1/runs/{id}/perf-regression", 999999)).andExpect(status().isNotFound());
	}

}
