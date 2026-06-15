package org.alexmond.unitrack.web;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The alert-channels page requires auth to view and owner access to mutate; secrets stay
 * masked.
 */
@SpringBootTest
class AlertsUiIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private ProjectRepository projects;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	private Long newProject(String name) {
		return this.projects.save(new Project(name, "https://github.com/acme/" + name)).getId();
	}

	@Test
	void anonymousIsSentToLogin() throws Exception {
		Long pid = newProject("alerts-ui-anon");
		mvc().perform(get("/projects/{id}/alerts", pid))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	@WithMockUser(username = "bob", roles = "USER")
	void nonOwnerCannotAddChannel() throws Exception {
		Long pid = newProject("alerts-ui-nonowner");
		mvc()
			.perform(post("/projects/{id}/alerts", pid).with(csrf())
				.param("type", "SLACK")
				.param("label", "#x")
				.param("secret", "https://hooks.slack.com/services/sekret"))
			.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "admin", roles = "ADMIN")
	void ownerAddsChannelAndSecretIsMaskedOnThePage() throws Exception {
		Long pid = newProject("alerts-ui-owner");
		MockMvc mvc = mvc();
		String secret = "https://hooks.slack.com/services/T/B/plain-sekret-token";

		mvc.perform(post("/projects/{id}/alerts", pid).with(csrf())
			.param("type", "SLACK")
			.param("label", "#builds")
			.param("tags", "GATE_FAILED")
			.param("secret", secret)).andExpect(status().is3xxRedirection());

		mvc.perform(get("/projects/{id}/alerts", pid))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("#builds")))
			.andExpect(content().string(not(containsString("plain-sekret-token"))));
	}

}
