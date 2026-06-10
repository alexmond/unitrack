package org.alexmond.unitrack.web;

import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
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
class IngestPrMetadataIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private static final byte[] JUNIT = ("<?xml version=\"1.0\"?><testsuite name=\"s\" tests=\"1\" failures=\"0\" "
			+ "errors=\"0\" skipped=\"0\" time=\"0.1\"><testcase name=\"t\" classname=\"com.x.X\" time=\"0.1\"/>"
			+ "</testsuite>")
		.getBytes();

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	void prMetadataIsStoredAndShownOnRunDetail() throws Exception {
		MockMvc mvc = mockMvc();
		String body = mvc
			.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST.xml", "text/xml", JUNIT))
				.param("project", "pr-meta")
				.param("branch", "feature/x")
				.param("commit", "abc123")
				.param("baseBranch", "main")
				.param("prNumber", "42"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long runId = ((Number) JsonPath.read(body, "$.runId")).longValue();

		mvc.perform(get("/runs/{id}", runId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("PR #42")))
			.andExpect(content().string(containsString("feature/x")));
	}

	@Test
	void ordinaryBuildHasNoPrBadge() throws Exception {
		MockMvc mvc = mockMvc();
		String body = mvc
			.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST.xml", "text/xml", JUNIT))
				.param("project", "pr-meta-none")
				.param("branch", "main")
				.param("commit", "def456"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long runId = ((Number) JsonPath.read(body, "$.runId")).longValue();

		mvc.perform(get("/runs/{id}", runId))
			.andExpect(status().isOk())
			.andExpect(content().string(Matchers.not(containsString("PR #"))));
	}

}
