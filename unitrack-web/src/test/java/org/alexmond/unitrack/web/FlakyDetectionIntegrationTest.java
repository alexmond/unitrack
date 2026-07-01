package org.alexmond.unitrack.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
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
@WithMockUser("admin")
class FlakyDetectionIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private static byte[] junit(boolean failing) {
		String body = failing
				? "<testcase name=\"a\" classname=\"com.x.T\" time=\"0.01\">"
						+ "<failure message=\"boom\" type=\"java.lang.AssertionError\">trace</failure></testcase>"
				: "<testcase name=\"a\" classname=\"com.x.T\" time=\"0.01\"/>";
		int failures = failing ? 1 : 0;
		return ("<?xml version=\"1.0\"?><testsuite name=\"com.x.T\" tests=\"1\" failures=\"" + failures
				+ "\" errors=\"0\" skipped=\"0\" time=\"0.01\">" + body + "</testsuite>")
			.getBytes();
	}

	private long ingest(MockMvc mvc, String project, String commit, boolean failing) throws Exception {
		String content = mvc
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("junit", "TEST-T.xml", "text/xml", junit(failing)))
				.param("project", project)
				.param("commit", commit))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(content, "$.projectId")).longValue();
	}

	@Test
	void detectsAndQuarantinesAFlakyTest() throws Exception {
		MockMvc mvc = mockMvc();
		// Same commit, one passing run and one failing run -> flaky.
		long projectId = ingest(mvc, "flaky-demo", "deadbeef", false);
		ingest(mvc, "flaky-demo", "deadbeef", true);

		mvc.perform(get("/api/v1/projects/{id}/flaky", projectId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].name").value("a"))
			.andExpect(jsonPath("$[0].className").value("com.x.T"))
			.andExpect(jsonPath("$[0].flakyCommits").value(1))
			.andExpect(jsonPath("$[0].failures").value(1))
			.andExpect(jsonPath("$[0].totalResults").value(2))
			.andExpect(jsonPath("$[0].status").value("ACTIVE"));

		// Quarantine it via REST.
		mvc.perform(post("/api/v1/projects/{id}/flaky/status", projectId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"className\":\"com.x.T\",\"name\":\"a\",\"status\":\"QUARANTINED\",\"note\":\"known\"}"))
			.andExpect(status().isAccepted());

		mvc.perform(get("/api/v1/projects/{id}/flaky", projectId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].status").value("QUARANTINED"));

		// Flaky tests are folded into the Tests page (epic #390).
		mvc.perform(get("/projects/{id}/tests", projectId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("com.x.T#a")));
	}

	@Test
	void consistentlyFailingTestIsNotFlaky() throws Exception {
		MockMvc mvc = mockMvc();
		// Two failing runs of the same commit: a real failure, not flaky.
		long projectId = ingest(mvc, "broken-demo", "cafe", true);
		ingest(mvc, "broken-demo", "cafe", true);

		mvc.perform(get("/api/v1/projects/{id}/flaky", projectId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
	}

}
