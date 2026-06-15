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

@SpringBootTest
class RunExportIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private long ingestFailingRun(MockMvc mvc) throws Exception {
		byte[] xml = ("<?xml version=\"1.0\"?><testsuite name=\"com.x.ExportSuite\" tests=\"1\" failures=\"1\" "
				+ "errors=\"0\" skipped=\"0\" time=\"0.01\">"
				+ "<testcase name=\"boom\" classname=\"com.x.ExportSuite\" time=\"0.01\">"
				+ "<failure message=\"kaboom\" type=\"java.lang.AssertionError\">trace-here</failure></testcase>"
				+ "</testsuite>")
			.getBytes();
		String content = mvc
			.perform(
					multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST-Export.xml", "text/xml", xml))
						.param("project", "export-demo")
						.param("branch", "main")
						.param("commit", "deadbeef"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(content, "$.runId")).longValue();
	}

	@Test
	void exportRendersSelfContainedHtml() throws Exception {
		MockMvc mvc = mockMvc();
		long runId = ingestFailingRun(mvc);

		mvc.perform(get("/runs/{id}/export", runId))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith("text/html"))
			// the failing case is shown
			.andExpect(content().string(containsString("com.x.ExportSuite#boom")))
			.andExpect(content().string(containsString("kaboom")))
			// self-contained: no external stylesheet/script references
			.andExpect(content().string(not(containsString("<link"))))
			.andExpect(content().string(not(containsString("/webjars/"))));
	}

	@Test
	void unknownRunIsNotFound() throws Exception {
		mockMvc().perform(get("/runs/{id}/export", 999999)).andExpect(status().isNotFound());
	}

}
