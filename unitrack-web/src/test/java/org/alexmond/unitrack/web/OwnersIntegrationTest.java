package org.alexmond.unitrack.web;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.Visibility;
import org.alexmond.unitrack.report.OwnershipService;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Test-ownership rules: page renders for readers, writes are membership-gated. */
@SpringBootTest
class OwnersIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private ProjectRepository projects;

	@Autowired
	private OwnershipService ownership;

	private Long projectId;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@BeforeEach
	void seed() {
		Project p = projects.findByName("owners-proj").orElseGet(() -> projects.save(new Project("owners-proj", null)));
		p.setVisibility(Visibility.PUBLIC);
		projectId = projects.save(p).getId();
	}

	@Test
	void pageRendersAndAddIsWriteGated() throws Exception {
		MockMvc mvc = mvc();
		mvc.perform(get("/projects/{id}/owners", projectId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Test owners")));

		// Anonymous can't add a rule.
		mvc.perform(post("/projects/{id}/owners/rules", projectId).with(csrf())
			.param("owner", "@x")
			.param("pattern", "com\\.x\\..*")).andExpect(status().isForbidden());

		// Admin can.
		mvc.perform(post("/projects/{id}/owners/rules", projectId).with(csrf())
			.param("owner", "@payments")
			.param("pattern", "com\\.billing\\..*")
			.with(user("admin"))).andExpect(status().is3xxRedirection());
		assertThat(ownership.listRules(projectId)).anyMatch((r) -> r.owner().equals("@payments"));
	}

}
