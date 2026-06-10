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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PrPageIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private static final byte[] JUNIT = ("<?xml version=\"1.0\"?><testsuite name=\"s\" tests=\"1\" failures=\"0\" "
			+ "errors=\"0\" skipped=\"0\" time=\"0.1\"><testcase name=\"t\" classname=\"com.x.X\" time=\"0.1\"/>"
			+ "</testsuite>")
		.getBytes();

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private long ingestPr(MockMvc mvc, String project, String commit) throws Exception {
		String body = mvc
			.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST.xml", "text/xml", JUNIT))
				.param("project", project)
				.param("branch", "feature/x")
				.param("commit", commit)
				.param("baseBranch", "main")
				.param("prNumber", "42"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.projectId")).longValue();
	}

	@Test
	void prPageAndOverviewSectionRender() throws Exception {
		MockMvc mvc = mockMvc();
		long projectId = ingestPr(mvc, "pr-page", "abc1");
		ingestPr(mvc, "pr-page", "def2");

		mvc.perform(get("/projects/{id}/pr/{pr}", projectId, 42))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("PR #42")))
			.andExpect(content().string(containsString("Pull request")))
			.andExpect(content().string(containsString("feature/x")))
			.andExpect(content().string(containsString("main")))
			.andExpect(content().string(containsString("Runs on this PR")));

		// Overview lists the PR.
		mvc.perform(get("/projects/{id}", projectId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Pull requests")))
			.andExpect(content().string(containsString("#42")));
	}

	@Test
	void unknownPrIsNotFound() throws Exception {
		MockMvc mvc = mockMvc();
		long projectId = ingestPr(mvc, "pr-page-404", "x1");
		mvc.perform(get("/projects/{id}/pr/{pr}", projectId, 999)).andExpect(status().isNotFound());
	}

}
