package org.alexmond.unitrack.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PerformanceIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	/** Three cases a/b/c with the given per-test times (seconds). */
	private static byte[] junit(double aSec, double bSec, double cSec) {
		String body = "<?xml version=\"1.0\"?><testsuite name=\"com.x.G\" tests=\"3\" failures=\"0\" errors=\"0\" "
				+ "skipped=\"0\" time=\"" + (aSec + bSec + cSec) + "\">" + tc("a", aSec) + tc("b", bSec) + tc("c", cSec)
				+ "</testsuite>";
		return body.getBytes();
	}

	private static String tc(String name, double sec) {
		return "<testcase name=\"" + name + "\" classname=\"com.x.G\" time=\"" + sec + "\"/>";
	}

	private long ingest(MockMvc mvc, String project, String commit, double a, double b, double c) throws Exception {
		String content = mvc
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("junit", "TEST-G.xml", "text/xml", junit(a, b, c)))
				.param("project", project)
				.param("branch", "main")
				.param("commit", commit))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(content, "$.projectId")).longValue();
	}

	@Test
	void ranksSlowestTestsAndChartsSuiteTime() throws Exception {
		MockMvc mvc = mockMvc();
		ingest(mvc, "perf-demo", "c1", 0.10, 0.05, 0.02);
		long projectId = ingest(mvc, "perf-demo", "c2", 0.50, 0.10, 0.30); // latest run

		// Slowest in the latest run: a (500ms) > c (300ms) > b (100ms).
		mvc.perform(get("/api/v1/projects/{id}/performance", projectId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.slowestInLatestRun[0].name").value("a"))
			.andExpect(jsonPath("$.slowestInLatestRun[0].durationMs").value(500))
			.andExpect(jsonPath("$.slowestInLatestRun[1].name").value("c"))
			.andExpect(jsonPath("$.slowestInLatestRun[2].name").value("b"))
			.andExpect(jsonPath("$.suiteTimeTrend.length()").value(2));

		// Per-test duration trend for 'a' across both runs (oldest first: 100ms then
		// 500ms).
		mvc.perform(
				get("/api/v1/projects/{id}/test-duration", projectId).param("className", "com.x.G").param("name", "a"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("a"))
			.andExpect(jsonPath("$.points.length()").value(2))
			.andExpect(jsonPath("$.points[0].durationMs").value(100))
			.andExpect(jsonPath("$.points[1].durationMs").value(500));

		// The performance dashboard page renders.
		mvc.perform(get("/projects/{id}/performance", projectId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Slowest tests")))
			// With runs present the empty state must NOT render (th:replace/th:unless
			// precedence trap).
			.andExpect(content().string(not(containsString("No test timings yet"))));
	}

	@Test
	void unknownProjectPerformanceIsNotFound() throws Exception {
		mockMvc().perform(get("/api/v1/projects/{id}/performance", 999999)).andExpect(status().isNotFound());
	}

}
