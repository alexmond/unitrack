package org.alexmond.unitrack.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class CoverageFormatsIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private static final byte[] JUNIT = ("<?xml version=\"1.0\"?><testsuite name=\"S\" tests=\"1\" failures=\"0\" "
			+ "errors=\"0\" skipped=\"0\" time=\"0.01\"><testcase name=\"t\" classname=\"S\" time=\"0.01\"/></testsuite>")
		.getBytes();

	// 2 of 3 lines covered -> 66.7% line coverage; detected as Cobertura by content.
	private static final byte[] COBERTURA = ("<?xml version=\"1.0\"?><coverage line-rate=\"0.66\">"
			+ "<packages><package name=\"app\"><classes><class name=\"app.foo\" filename=\"app/foo.py\"><lines>"
			+ "<line number=\"1\" hits=\"1\"/><line number=\"2\" hits=\"1\"/><line number=\"3\" hits=\"0\"/>"
			+ "</lines></class></classes></package></packages></coverage>")
		.getBytes();

	@Test
	void ingestsCoberturaUnderTheCoverageField() throws Exception {
		MockMvc mvc = mockMvc();
		String body = mvc
			.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST-S.xml", "text/xml", JUNIT))
				.file(new MockMultipartFile("jacoco", "coverage.xml", "text/xml", COBERTURA))
				.param("project", "cobertura-demo")
				.param("commit", "c1"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.lineCoveragePct").value(closeTo(66.67, 0.1)))
			.andReturn()
			.getResponse()
			.getContentAsString();

		long runId = ((Number) JsonPath.read(body, "$.runId")).longValue();
		mvc.perform(get("/api/v1/runs/{id}", runId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.coverage.linePct").value(closeTo(66.67, 0.1)))
			.andExpect(jsonPath("$.coverage.files[0].path").value("app/app/foo.py"));
	}

}
