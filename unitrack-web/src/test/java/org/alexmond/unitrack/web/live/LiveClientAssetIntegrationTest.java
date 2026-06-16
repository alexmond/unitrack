package org.alexmond.unitrack.web.live;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The live-updates client asset is served and wired into every page. */
@SpringBootTest
class LiveClientAssetIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	void indexPageIncludesTheLiveIndicatorAndScript() throws Exception {
		mvc().perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("id=\"live-dot\"")))
			.andExpect(content().string(containsString("/js/live.js")));
	}

	@Test
	void liveScriptIsServedAndConnectsToTheEventStream() throws Exception {
		mvc().perform(get("/js/live.js"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("/api/v1/events")))
			.andExpect(content().string(containsString("unitrack:run")));
	}

}
