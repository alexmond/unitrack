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

/** Overview branch picker (#180): trends + recent runs scope to the selected branch. */
@SpringBootTest
class BranchSelectorIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private static final byte[] JUNIT = ("<?xml version=\"1.0\"?><testsuite name=\"s\" tests=\"1\" failures=\"0\" "
			+ "errors=\"0\" skipped=\"0\" time=\"0.1\"><testcase name=\"t\" classname=\"com.x.X\" time=\"0.1\"/>"
			+ "</testsuite>")
		.getBytes();

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private long ingest(MockMvc mvc, String project, String branch, String commit) throws Exception {
		String body = mvc
			.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST.xml", "text/xml", JUNIT))
				.param("project", project)
				.param("branch", branch)
				.param("commit", commit))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.projectId")).longValue();
	}

	@Test
	void overviewDefaultsToBaseBranchAndScopesToSelectedBranch() throws Exception {
		MockMvc mvc = mockMvc();
		// Short SHAs (first 7 chars) appear in the runs table + hero — use them as branch
		// markers.
		long projectId = ingest(mvc, "branch-sel", "main", "aaaaaaa11111");
		ingest(mvc, "branch-sel", "feature/x", "bbbbbbb22222");

		// No param → defaults to the base branch (main): only main's run is shown.
		mvc.perform(get("/projects/{id}", projectId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("All branches")))
			.andExpect(content().string(containsString("feature/x")))
			.andExpect(content().string(containsString("aaaaaaa")))
			.andExpect(content().string(not(containsString("bbbbbbb"))));

		// Explicit branch → scoped to feature/x only.
		mvc.perform(get("/projects/{id}", projectId).param("branch", "feature/x"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("bbbbbbb")))
			.andExpect(content().string(not(containsString("aaaaaaa"))));

		// Empty param → "All branches": both runs shown.
		mvc.perform(get("/projects/{id}", projectId).param("branch", ""))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("aaaaaaa")))
			.andExpect(content().string(containsString("bbbbbbb")));
	}

	@Test
	void branchesSectionListsEachBranchAndMarksTheDefault() throws Exception {
		MockMvc mvc = mockMvc();
		long projectId = ingest(mvc, "branch-list", "main", "cccccccc1111");
		ingest(mvc, "branch-list", "release/1.0", "dddddddd2222");

		mvc.perform(get("/projects/{id}", projectId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Branches")))
			.andExpect(content().string(containsString("release/1.0")))
			// The gate base branch (main) carries the default-branch marker (not the flag
			// cell).
			.andExpect(content().string(containsString("tag\">default</span>")));
	}

}
