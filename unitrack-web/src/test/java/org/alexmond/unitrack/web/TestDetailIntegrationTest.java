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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer MockMvc integration tests for {@code GET
 * /projects/{id}/test?className=&name=} (criteria 4–7 of #170 per-test page feature).
 */
@SpringBootTest
class TestDetailIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private static byte[] junitXml(boolean failing) {
		String tc = failing
				? "<testcase name=\"myTest\" classname=\"com.example.MyClass\" time=\"0.05\">"
						+ "<failure message=\"assert\" type=\"java.lang.AssertionError\">trace</failure></testcase>"
				: "<testcase name=\"myTest\" classname=\"com.example.MyClass\" time=\"0.05\"/>";
		return ("<?xml version=\"1.0\"?><testsuite name=\"com.example.MyClass\" tests=\"1\" failures=\""
				+ (failing ? 1 : 0) + "\" errors=\"0\" skipped=\"0\" time=\"0.05\">" + tc + "</testsuite>")
			.getBytes();
	}

	private static byte[] junitXmlAlwaysPass() {
		return ("<?xml version=\"1.0\"?><testsuite name=\"com.example.MyClass\" tests=\"1\" failures=\"0\""
				+ " errors=\"0\" skipped=\"0\" time=\"0.02\">"
				+ "<testcase name=\"alwaysPass\" classname=\"com.example.MyClass\" time=\"0.02\"/>" + "</testsuite>")
			.getBytes();
	}

	/**
	 * Ingest one run and return the project id (derived from response, never assumed).
	 */
	private long ingest(MockMvc mvc, String project, String commit, boolean failing) throws Exception {
		String body = mvc
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("junit", "TEST-MyClass.xml", "text/xml", junitXml(failing)))
				.param("project", project)
				.param("branch", "main")
				.param("commit", commit))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.projectId")).longValue();
	}

	/** Ingest a run that only contains the always-passing test (separate suite). */
	private long ingestAlwaysPass(MockMvc mvc, String project, String commit) throws Exception {
		String body = mvc
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("junit", "TEST-MyClass.xml", "text/xml", junitXmlAlwaysPass()))
				.param("project", project)
				.param("branch", "main")
				.param("commit", commit))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.projectId")).longValue();
	}

	/**
	 * Criteria 4 + 5: timeline strip contains both a passing cell (class "pass") and a
	 * failing cell (class "fail"); durChart canvas and non-empty trendMs JSON are
	 * present.
	 */
	@Test
	void timelineAndSparklineRendered() throws Exception {
		MockMvc mvc = mockMvc();
		// Run 1: pass, Run 2: fail — produces a mixed timeline
		long projectId = ingest(mvc, "test-detail-timeline", "sha-pass1", false);
		ingest(mvc, "test-detail-timeline", "sha-fail1", true);

		mvc.perform(
				get("/projects/{id}/test", projectId).param("className", "com.example.MyClass").param("name", "myTest"))
			.andExpect(status().isOk())
			// Criterion 4: timeline strip present with both cell classes
			.andExpect(content().string(containsString("id=\"test-timeline\"")))
			.andExpect(content().string(containsString("class=\"pass\"")))
			.andExpect(content().string(containsString("class=\"fail\"")))
			// Criterion 5: sparkline canvas and non-empty trendMs
			.andExpect(content().string(containsString("id=\"durChart\"")))
			.andExpect(content().string(not(containsString("trendMs = JSON.parse('[]')"))));
	}

	/**
	 * Criterion 6 (failing path): for a currently-failing test the "First failing" blame
	 * block is rendered inside {@code #test-blame} and no "Not currently failing" message
	 * appears.
	 */
	@Test
	void blameShownForCurrentlyFailingTest() throws Exception {
		MockMvc mvc = mockMvc();
		long projectId = ingest(mvc, "test-detail-blame-fail", "sha-green", false);
		ingest(mvc, "test-detail-blame-fail", "sha-red", true);

		mvc.perform(
				get("/projects/{id}/test", projectId).param("className", "com.example.MyClass").param("name", "myTest"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("id=\"test-blame\"")))
			.andExpect(content().string(containsString("First failing:")))
			.andExpect(content().string(not(containsString("Not currently failing"))));
	}

	/**
	 * Criterion 6 (passing path): for a currently-passing test the "Not currently
	 * failing" message is shown and there is no "First failing" text.
	 */
	@Test
	void noBlameForCurrentlyPassingTest() throws Exception {
		MockMvc mvc = mockMvc();
		long projectId = ingestAlwaysPass(mvc, "test-detail-blame-pass", "sha-p1");
		ingestAlwaysPass(mvc, "test-detail-blame-pass", "sha-p2");

		mvc.perform(get("/projects/{id}/test", projectId).param("className", "com.example.MyClass")
			.param("name", "alwaysPass"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("id=\"test-blame\"")))
			.andExpect(content().string(containsString("Not currently failing")))
			.andExpect(content().string(not(containsString("First failing:"))));
	}

	/**
	 * Criterion 7: an unknown (className, name) pair returns 200 with the empty-state
	 * message and zero timeline cells (no {@code class="pass"} or {@code class="fail"}).
	 */
	@Test
	void emptyStateForUnknownTest() throws Exception {
		MockMvc mvc = mockMvc();
		long projectId = ingest(mvc, "test-detail-empty", "sha-e1", false);

		mvc.perform(get("/projects/{id}/test", projectId).param("className", "com.example.Unknown")
			.param("name", "noSuchTest"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("No history for this test")))
			.andExpect(content().string(not(containsString("class=\"pass\""))))
			.andExpect(content().string(not(containsString("class=\"fail\""))));
	}

}
