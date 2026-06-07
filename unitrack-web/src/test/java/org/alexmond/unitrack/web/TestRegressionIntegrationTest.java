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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class TestRegressionIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	/** A suite of three cases; each flag toggles whether that case fails. */
	private static byte[] junit(boolean aFails, boolean bFails, boolean cFails) {
		int failures = (aFails ? 1 : 0) + (bFails ? 1 : 0) + (cFails ? 1 : 0);
		String suite = "<?xml version=\"1.0\"?><testsuite name=\"com.x.G\" tests=\"3\" failures=\"" + failures
				+ "\" errors=\"0\" skipped=\"0\" time=\"0.03\">" + tc("a", aFails) + tc("b", bFails) + tc("c", cFails)
				+ "</testsuite>";
		return suite.getBytes();
	}

	private static String tc(String name, boolean fails) {
		if (fails) {
			return "<testcase name=\"" + name + "\" classname=\"com.x.G\" time=\"0.01\">"
					+ "<failure message=\"boom\" type=\"java.lang.AssertionError\">trace</failure></testcase>";
		}
		return "<testcase name=\"" + name + "\" classname=\"com.x.G\" time=\"0.01\"/>";
	}

	private long ingest(MockMvc mvc, String project, String commit, boolean aFails, boolean bFails, boolean cFails)
			throws Exception {
		String content = mvc
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("junit", "TEST-G.xml", "text/xml", junit(aFails, bFails, cFails)))
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
	void diffsNewFailuresAndFixesAgainstBaseline() throws Exception {
		MockMvc mvc = mockMvc();
		// Baseline on main: 'a' fails, 'b'/'c' pass.
		long baselineRunId = ingest(mvc, "regress-demo", "basecommit", true, false, false);
		// Later run: 'a' fixed, 'b' still passes, 'c' newly fails.
		long currentRunId = ingest(mvc, "regress-demo", "newcommit", false, false, true);

		mvc.perform(get("/api/v1/runs/{id}/regression", currentRunId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.baselineFound").value(true))
			.andExpect(jsonPath("$.baselineRunId").value((int) baselineRunId))
			.andExpect(jsonPath("$.baseBranch").value("main"))
			.andExpect(jsonPath("$.newFailures.length()").value(1))
			.andExpect(jsonPath("$.newFailures[0].name").value("c"))
			.andExpect(jsonPath("$.newFailures[0].failureType").value("java.lang.AssertionError"))
			.andExpect(jsonPath("$.newPasses.length()").value(1))
			.andExpect(jsonPath("$.newPasses[0].name").value("a"))
			.andExpect(jsonPath("$.stillFailing.length()").value(0));

		// The run page renders the regression section.
		mvc.perform(get("/runs/{id}", currentRunId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Regression vs baseline")));
	}

	@Test
	void reportsNoBaselineForTheFirstRun() throws Exception {
		MockMvc mvc = mockMvc();
		long firstRunId = ingest(mvc, "regress-first", "c1", true, false, false);

		mvc.perform(get("/api/v1/runs/{id}/regression", firstRunId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.baselineFound").value(false))
			.andExpect(jsonPath("$.baselineRunId").doesNotExist())
			.andExpect(jsonPath("$.newFailures.length()").value(1))
			.andExpect(jsonPath("$.newFailures[0].name").value("a"))
			.andExpect(jsonPath("$.newPasses.length()").value(0));
	}

	@Test
	void unknownRunReturnsNotFound() throws Exception {
		mockMvc().perform(get("/api/v1/runs/{id}/regression", 999999)).andExpect(status().isNotFound());
	}

}
