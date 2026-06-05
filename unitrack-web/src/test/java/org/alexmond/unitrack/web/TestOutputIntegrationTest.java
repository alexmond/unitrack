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
class TestOutputIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private static final byte[] JUNIT = ("<?xml version=\"1.0\"?>"
			+ "<testsuite name=\"com.x.O\" tests=\"1\" failures=\"1\" errors=\"0\" skipped=\"0\" time=\"0.01\">"
			+ "<testcase name=\"t\" classname=\"com.x.O\" time=\"0.01\">"
			+ "<failure message=\"boom\" type=\"java.lang.AssertionError\">trace</failure>"
			+ "<system-out>hello stdout [[ATTACHMENT|https://ci.example/screenshot.png]]</system-out>"
			+ "<system-err>some stderr</system-err>" + "</testcase></testsuite>")
		.getBytes();

	@Test
	void capturesSystemOutErrAndAttachments() throws Exception {
		MockMvc mvc = mockMvc();
		String content = mvc
			.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST-O.xml", "text/xml", JUNIT))
				.param("project", "output-demo")
				.param("commit", "outc"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long runId = ((Number) JsonPath.read(content, "$.runId")).longValue();

		mvc.perform(get("/api/v1/runs/{id}", runId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.failures[0].systemOut", containsString("hello stdout")))
			.andExpect(jsonPath("$.failures[0].systemErr").value("some stderr"))
			.andExpect(jsonPath("$.failures[0].attachments[0]").value("https://ci.example/screenshot.png"));

		mvc.perform(get("/runs/{id}", runId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("system-out")))
			.andExpect(content().string(containsString("screenshot.png")));
	}

}
