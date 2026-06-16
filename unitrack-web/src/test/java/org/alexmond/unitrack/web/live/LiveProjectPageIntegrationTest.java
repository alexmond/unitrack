package org.alexmond.unitrack.web.live;

import org.alexmond.unitrack.report.ReportingService;
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

/**
 * The project page's runs table + script carry the hooks the live client prepends into.
 */
@SpringBootTest
class LiveProjectPageIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private ReportingService reporting;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	void projectPageHasRunsBodyAndListener() throws Exception {
		MockMvc mvc = mvc();
		byte[] xml = ("<?xml version=\"1.0\"?><testsuite name=\"com.x.S\" tests=\"1\" failures=\"0\" errors=\"0\" "
				+ "skipped=\"0\" time=\"0.01\"><testcase name=\"ok\" classname=\"com.x.S\" time=\"0.01\"/></testsuite>")
			.getBytes();
		mvc.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST.xml", "text/xml", xml))
			.param("project", "live-proj-demo")
			.param("commit", "c1")).andExpect(status().isCreated());
		Long pid = this.reporting.findProjectByName("live-proj-demo").orElseThrow().getId();

		mvc.perform(get("/projects/{id}", pid))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("id=\"runs-body\"")))
			.andExpect(content().string(containsString("unitrack:run")));
	}

}
