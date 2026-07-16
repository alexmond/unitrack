package org.alexmond.unitrack.web.ui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The multi-provider "Import projects" page renders both provider tabs and, with neither
 * provider configured in the test profile, prompts for the right config keys per tab.
 */
@SpringBootTest
class ImportControllerTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void rendersBothTabsAndGitHubConfigHintByDefault() throws Exception {
		mvc().perform(get("/import").with(user("u")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Import projects")))
			.andExpect(content().string(containsString("GitHub")))
			.andExpect(content().string(containsString("GitLab")))
			.andExpect(content().string(containsString("unitrack.github")));
	}

	@Test
	void gitlabTabRendersItsOwnConfigHint() throws Exception {
		mvc().perform(get("/import").param("provider", "gitlab").with(user("u")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("unitrack.gitlab")));
	}

}
