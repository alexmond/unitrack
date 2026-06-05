package org.alexmond.unitrack.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class IngestIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	void uploadsResultsAndExposesThemViaApi() throws Exception {
		MockMvc mvc = mockMvc();
		byte[] junit = getClass().getResourceAsStream("/samples/surefire-sample.xml").readAllBytes();
		byte[] jacoco = getClass().getResourceAsStream("/samples/jacoco-sample.xml").readAllBytes();

		mvc.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST-Calc.xml", "text/xml", junit))
			.file(new MockMultipartFile("jacoco", "jacoco.xml", "text/xml", jacoco))
			.param("project", "demo")
			.param("branch", "main")
			.param("commit", "abcdef1234567890"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.project").value("demo"))
			.andExpect(jsonPath("$.total").value(4))
			.andExpect(jsonPath("$.passed").value(1))
			.andExpect(jsonPath("$.failed").value(1))
			.andExpect(jsonPath("$.errors").value(1))
			.andExpect(jsonPath("$.skipped").value(1))
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.lineCoveragePct", greaterThan(70.0)));

		mvc.perform(get("/api/v1/projects"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("demo"))
			.andExpect(jsonPath("$[0].runCount").value(1));

		mvc.perform(get("/api/v1/runs/1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.run.total").value(4))
			.andExpect(jsonPath("$.failures.length()").value(2))
			.andExpect(jsonPath("$.coverage.files.length()").value(2));

		// The Thymeleaf dashboard pages render without error.
		mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("demo")));
		mvc.perform(get("/projects/1"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Recent runs")));
		mvc.perform(get("/runs/1"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Coverage by file")));
	}

	@Test
	void rejectsUploadWithoutJunitFile() throws Exception {
		mockMvc().perform(multipart("/api/v1/ingest").param("project", "demo")).andExpect(status().isBadRequest());
	}

}
