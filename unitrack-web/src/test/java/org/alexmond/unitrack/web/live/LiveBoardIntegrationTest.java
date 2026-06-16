package org.alexmond.unitrack.web.live;

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

/** The board rows carry the data hooks + listener the live client updates in place. */
@SpringBootTest
class LiveBoardIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	void boardRowHasLiveHooksAndListener() throws Exception {
		MockMvc mvc = mvc();
		byte[] xml = ("<?xml version=\"1.0\"?><testsuite name=\"com.x.S\" tests=\"1\" failures=\"0\" errors=\"0\" "
				+ "skipped=\"0\" time=\"0.01\"><testcase name=\"ok\" classname=\"com.x.S\" time=\"0.01\"/></testsuite>")
			.getBytes();
		mvc.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST.xml", "text/xml", xml))
			.param("project", "live-board-demo")
			.param("commit", "c1")).andExpect(status().isCreated());

		mvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("data-project-id=")))
			.andExpect(content().string(containsString("data-live=\"pass\"")))
			.andExpect(content().string(containsString("unitrack:run")));
	}

}
