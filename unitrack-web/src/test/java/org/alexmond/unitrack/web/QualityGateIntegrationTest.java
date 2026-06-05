package org.alexmond.unitrack.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class QualityGateIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private static byte[] junit(boolean failing) {
		String tc = failing
				? "<testcase name=\"a\" classname=\"com.x.G\" time=\"0.01\">"
						+ "<failure message=\"boom\" type=\"java.lang.AssertionError\">trace</failure></testcase>"
				: "<testcase name=\"a\" classname=\"com.x.G\" time=\"0.01\"/>";
		return ("<?xml version=\"1.0\"?><testsuite name=\"com.x.G\" tests=\"1\" failures=\"" + (failing ? 1 : 0)
				+ "\" errors=\"0\" skipped=\"0\" time=\"0.01\">" + tc + "</testsuite>")
			.getBytes();
	}

	/** Ingests one run and returns {projectId, runId}. */
	private long[] ingest(MockMvc mvc, String commit, boolean failing) throws Exception {
		String content = mvc
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("junit", "TEST-G.xml", "text/xml", junit(failing)))
				.param("project", "gate-demo")
				.param("branch", "main")
				.param("commit", commit))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return new long[] { ((Number) JsonPath.read(content, "$.projectId")).longValue(),
				((Number) JsonPath.read(content, "$.runId")).longValue() };
	}

	@Test
	void gateFailsOnNewFailureAndPassesAfterQuarantine() throws Exception {
		MockMvc mvc = mockMvc();
		long[] base = ingest(mvc, "basecommit", false); // baseline: test passes
		long projectId = base[0];
		long baselineRunId = base[1];
		long failingRunId = ingest(mvc, "newcommit", true)[1]; // later run: same test now
																// fails

		// Baseline run itself is clean.
		mvc.perform(get("/api/v1/runs/{id}/quality-gate", baselineRunId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.passed").value(true));

		// The new run introduces a failure not present in the baseline -> gate fails.
		mvc.perform(get("/api/v1/runs/{id}/quality-gate", failingRunId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.passed").value(false))
			.andExpect(jsonPath("$.rules.length()").value(1))
			.andExpect(jsonPath("$.rules[0].name").value("new-failures"))
			.andExpect(jsonPath("$.rules[0].passed").value(false));

		// The run page shows the gate.
		mvc.perform(get("/runs/{id}", failingRunId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Quality gate")));

		// Quarantine the test -> it no longer counts as a new failure -> gate passes.
		mvc.perform(post("/api/v1/projects/{id}/flaky/status", projectId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"className\":\"com.x.G\",\"name\":\"a\",\"status\":\"QUARANTINED\"}"))
			.andExpect(status().isAccepted());

		mvc.perform(get("/api/v1/runs/{id}/quality-gate", failingRunId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.passed").value(true));
	}

}
