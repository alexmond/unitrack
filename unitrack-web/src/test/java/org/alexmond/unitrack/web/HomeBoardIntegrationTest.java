package org.alexmond.unitrack.web;

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

/** The home page is the global all-projects health board (#171). */
@SpringBootTest
class HomeBoardIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private static byte[] junit(int failures) {
		String body = (failures > 0)
				? "<testcase name=\"t\" classname=\"com.x.X\" time=\"0.1\"><failure message=\"boom\">x</failure></testcase>"
				: "<testcase name=\"t\" classname=\"com.x.X\" time=\"0.1\"/>";
		return ("<?xml version=\"1.0\"?><testsuite name=\"s\" tests=\"1\" failures=\"" + failures
				+ "\" errors=\"0\" skipped=\"0\" time=\"0.1\">" + body + "</testsuite>")
			.getBytes();
	}

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private void ingest(MockMvc mvc, String project, int failures) throws Exception {
		mvc.perform(multipart("/api/v1/ingest")
			.file(new MockMultipartFile("junit", "TEST.xml", "text/xml", junit(failures)))
			.param("project", project)
			.param("branch", "main")
			.param("commit", project + failures)).andExpect(status().isCreated());
	}

	@Test
	void homeRendersHealthBoardWithGateColumnsAndBothProjects() throws Exception {
		MockMvc mvc = mockMvc();
		ingest(mvc, "board-green", 0);
		ingest(mvc, "board-red", 1);

		mvc.perform(get("/"))
			.andExpect(status().isOk())
			// Board-specific columns (distinguish from the old card layout).
			.andExpect(content().string(containsString("Flaky")))
			.andExpect(content().string(containsString("Trend")))
			.andExpect(content().string(containsString("board-green")))
			.andExpect(content().string(containsString("board-red")))
			// The failing project surfaces a FAILED gate badge.
			.andExpect(content().string(containsString("FAILED")));
	}

}
