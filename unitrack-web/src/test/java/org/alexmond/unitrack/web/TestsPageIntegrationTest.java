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
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The reconciled Tests page ({@code /projects/{id}/tests}, epic #390) is the canonical
 * Tests tab: KPI tiles, trend, by-module breakdown, an all-tests roster, and the folded
 * Flaky and Failure-clusters sections. It is public like the other analytics tabs, and
 * the old {@code /flaky}, {@code /clusters}, and {@code /new-tests} URLs redirect to it.
 */
@SpringBootTest
class TestsPageIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	private static byte[] junitXml() {
		return ("<?xml version=\"1.0\"?><testsuite name=\"com.example.MyClass\" tests=\"1\" failures=\"0\""
				+ " errors=\"0\" skipped=\"0\" time=\"0.02\">"
				+ "<testcase name=\"myTest\" classname=\"com.example.MyClass\" time=\"0.02\"/></testsuite>")
			.getBytes();
	}

	private long ingest(MockMvc mvc, String project) throws Exception {
		String body = mvc
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("junit", "TEST-MyClass.xml", "text/xml", junitXml()))
				.param("project", project)
				.param("branch", "main")
				.param("commit", "sha1"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.projectId")).longValue();
	}

	@Test
	void publicPageShowsFoldedFlakyAndClusterSections() throws Exception {
		MockMvc mvc = mvc();
		long projectId = ingest(mvc, "tests-page-public");

		// No authenticated user — the analytics page is public.
		mvc.perform(get("/projects/{id}/tests", projectId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("All tests")))
			.andExpect(content().string(containsString("id=\"flaky-section\"")))
			.andExpect(content().string(containsString("id=\"clusters-section\"")))
			// With runs present the empty state must NOT render. Guards a Thymeleaf
			// precedence trap: th:replace (100) runs before th:unless (300) on the same
			// element, so the empty-state guard must live on a wrapper, not the replaced
			// div.
			.andExpect(content().string(not(containsString("No test runs yet"))));
	}

	/**
	 * An unknown {@code module} scope falls back to "all tests" (full roster, no module
	 * chip) rather than erroring — the page is never a dead end.
	 */
	@Test
	void unknownModuleFallsBackToAllTests() throws Exception {
		MockMvc mvc = mvc();
		long projectId = ingest(mvc, "tests-page-module");

		mvc.perform(get("/projects/{id}/tests", projectId).param("module", "does-not-exist"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("All tests")))
			.andExpect(content().string(not(containsString("Module: <code"))));
	}

	/**
	 * The folded-away tabs' URLs redirect to the Tests page so bookmarks keep working.
	 */
	@Test
	void oldTabUrlsRedirectToTests() throws Exception {
		MockMvc mvc = mvc();
		long projectId = ingest(mvc, "tests-page-redirects");

		mvc.perform(get("/projects/{id}/new-tests", projectId))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/projects/" + projectId + "/tests"));
		mvc.perform(get("/projects/{id}/flaky", projectId))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/projects/" + projectId + "/tests#flaky-section"));
		mvc.perform(get("/projects/{id}/clusters", projectId))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/projects/" + projectId + "/tests#clusters-section"));
	}

}
