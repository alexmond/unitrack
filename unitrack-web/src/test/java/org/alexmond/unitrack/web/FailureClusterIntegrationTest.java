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
class FailureClusterIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	// Two AssertionErrors that differ only in numbers/hex (same normalized signature),
	// plus one NPE (a separate cluster).
	private static final byte[] JUNIT = ("<?xml version=\"1.0\"?>"
			+ "<testsuite name=\"com.x.C\" tests=\"3\" failures=\"3\" errors=\"0\" skipped=\"0\" time=\"0.03\">"
			+ "<testcase name=\"a\" classname=\"com.x.C\" time=\"0.01\">"
			+ "<failure message=\"expected 1 but was 2 at id 0x1a2b\" type=\"java.lang.AssertionError\"/></testcase>"
			+ "<testcase name=\"b\" classname=\"com.x.C\" time=\"0.01\">"
			+ "<failure message=\"expected 3 but was 4 at id 0x9f8e\" type=\"java.lang.AssertionError\"/></testcase>"
			+ "<testcase name=\"c\" classname=\"com.x.C\" time=\"0.01\">"
			+ "<failure message=\"npe\" type=\"java.lang.NullPointerException\"/></testcase>" + "</testsuite>")
		.getBytes();

	@Test
	void groupsSimilarFailuresIntoClusters() throws Exception {
		MockMvc mvc = mockMvc();
		String content = mvc
			.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST-C.xml", "text/xml", JUNIT))
				.param("project", "cluster-demo")
				.param("commit", "clusterc"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long projectId = ((Number) JsonPath.read(content, "$.projectId")).longValue();

		mvc.perform(get("/api/v1/projects/{id}/failure-clusters", projectId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].failureType").value("java.lang.AssertionError"))
			.andExpect(jsonPath("$[0].occurrences").value(2))
			.andExpect(jsonPath("$[0].distinctTests").value(2))
			.andExpect(jsonPath("$[0].tests.length()").value(2))
			.andExpect(jsonPath("$[1].failureType").value("java.lang.NullPointerException"))
			.andExpect(jsonPath("$[1].occurrences").value(1));

		// Failure clusters are folded into the Tests page (epic #390).
		mvc.perform(get("/projects/{id}/tests", projectId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Failure clusters")))
			.andExpect(content().string(containsString("2×")));
	}

}
