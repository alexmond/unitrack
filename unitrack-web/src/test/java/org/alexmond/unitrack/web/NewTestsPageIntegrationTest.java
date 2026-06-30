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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The internal preview at {@code /projects/{id}/new-tests} (reconciled Tests page, epic
 * #390) is login-gated even in open mode — anonymous visitors are redirected to login, a
 * logged-in user gets the page with Flaky and Failure-clusters folded in as sections.
 */
@SpringBootTest
class NewTestsPageIntegrationTest {

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
	void anonymousIsRedirectedToLogin() throws Exception {
		MockMvc mvc = mvc();
		long projectId = ingest(mvc, "new-tests-anon");

		mvc.perform(get("/projects/{id}/new-tests", projectId))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	void loggedInUserSeesPreviewWithFoldedSections() throws Exception {
		MockMvc mvc = mvc();
		long projectId = ingest(mvc, "new-tests-auth");

		mvc.perform(get("/projects/{id}/new-tests", projectId).with(user("admin").roles("ADMIN")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Internal preview")))
			.andExpect(content().string(containsString("id=\"flaky-section\"")))
			.andExpect(content().string(containsString("id=\"clusters-section\"")));
	}

}
