package org.alexmond.unitrack.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class TriageIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private static final byte[] JUNIT = ("<?xml version=\"1.0\"?>"
			+ "<testsuite name=\"com.x.T\" tests=\"2\" failures=\"2\" errors=\"0\" skipped=\"0\" time=\"0.02\">"
			+ "<testcase name=\"a\" classname=\"com.x.T\" time=\"0.01\">"
			+ "<failure message=\"heap\" type=\"java.lang.OutOfMemoryError\">"
			+ "java.lang.OutOfMemoryError: Java heap space</failure></testcase>"
			+ "<testcase name=\"b\" classname=\"com.x.T\" time=\"0.01\">"
			+ "<failure message=\"npe\" type=\"java.lang.NullPointerException\">npe trace</failure></testcase>"
			+ "</testsuite>")
		.getBytes();

	@Test
	void rulesCategorizeFailures() throws Exception {
		MockMvc mvc = mockMvc();
		String ingest = mvc
			.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST-T.xml", "text/xml", JUNIT))
				.param("project", "triage-demo")
				.param("commit", "triagec"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long projectId = ((Number) JsonPath.read(ingest, "$.projectId")).longValue();
		long runId = ((Number) JsonPath.read(ingest, "$.runId")).longValue();

		// Add a rule that flags OOMs as infrastructure.
		String created = mvc.perform(post("/api/v1/projects/{id}/triage-rules", projectId)
			.contentType(MediaType.APPLICATION_JSON)
			.content(
					"{\"name\":\"OOM\",\"category\":\"INFRASTRUCTURE\",\"pattern\":\"OutOfMemoryError\",\"priority\":10}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.category").value("INFRASTRUCTURE"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long ruleId = ((Number) JsonPath.read(created, "$.id")).longValue();

		mvc.perform(get("/api/v1/projects/{id}/triage-rules", projectId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].name").value("OOM"));

		// The OOM failure is categorized; the NPE stays untriaged.
		mvc.perform(get("/api/v1/runs/{id}/triage", runId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.failures[0].test").value("com.x.T#a"))
			.andExpect(jsonPath("$.failures[0].category").value("INFRASTRUCTURE"))
			.andExpect(jsonPath("$.failures[1].category").value("untriaged"));

		// Category badge shows on the run page.
		mvc.perform(get("/runs/{id}", runId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("INFRASTRUCTURE")));

		// Deleting the rule un-categorizes the failure.
		mvc.perform(delete("/api/v1/triage-rules/{ruleId}", ruleId)).andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/runs/{id}/triage", runId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.failures[0].category").value("untriaged"));
	}

}
