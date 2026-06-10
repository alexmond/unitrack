package org.alexmond.unitrack.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PullRequestGroupingIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private static final byte[] JUNIT = ("<?xml version=\"1.0\"?><testsuite name=\"s\" tests=\"1\" failures=\"0\" "
			+ "errors=\"0\" skipped=\"0\" time=\"0.1\"><testcase name=\"t\" classname=\"com.x.X\" time=\"0.1\"/>"
			+ "</testsuite>")
		.getBytes();

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private long ingest(MockMvc mvc, String project, String branch, String commit, Integer pr) throws Exception {
		MockMultipartHttpServletRequestBuilder req = multipart("/api/v1/ingest")
			.file(new MockMultipartFile("junit", "TEST.xml", "text/xml", JUNIT))
			.param("project", project)
			.param("branch", branch)
			.param("commit", commit);
		if (pr != null) {
			req = (MockMultipartHttpServletRequestBuilder) req.param("baseBranch", "main")
				.param("prNumber", pr.toString());
		}
		String body = mvc.perform(req).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.projectId")).longValue();
	}

	@Test
	void groupsRunsByPullRequestAndExcludesBranchBuilds() throws Exception {
		MockMvc mvc = mockMvc();
		long projectId = ingest(mvc, "pr-group", "feature/a", "a1", 42);
		ingest(mvc, "pr-group", "feature/a", "a2", 42); // second run on PR 42
		ingest(mvc, "pr-group", "feature/b", "b1", 7); // PR 7
		ingest(mvc, "pr-group", "main", "m1", null); // ordinary branch build — must not
														// appear

		mvc.perform(get("/api/v1/projects/{id}/pull-requests", projectId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[?(@.number == 42)].runCount").value(hasItem(2)))
			.andExpect(jsonPath("$[?(@.number == 42)].headBranch").value(hasItem("feature/a")))
			.andExpect(jsonPath("$[?(@.number == 42)].baseBranch").value(hasItem("main")))
			.andExpect(jsonPath("$[?(@.number == 42)].lastStatus").value(hasItem("PASSED")))
			.andExpect(jsonPath("$[?(@.number == 7)].runCount").value(hasItem(1)));
	}

	@Test
	void unknownProjectIsNotFound() throws Exception {
		mockMvc().perform(get("/api/v1/projects/{id}/pull-requests", 999999)).andExpect(status().isNotFound());
	}

}
