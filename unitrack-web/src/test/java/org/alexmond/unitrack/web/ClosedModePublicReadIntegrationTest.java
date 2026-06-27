package org.alexmond.unitrack.web;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.Visibility;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Closed mode ({@code open-mode=false}) is "public-readable": anonymous visitors may READ
 * public projects (private ones 404 via the controller), but all writes/management
 * require a login. This covers the security-filter side of the unlock that the open-mode
 * visibility test can't exercise.
 */
@SpringBootTest(properties = "unitrack.security.open-mode=false")
class ClosedModePublicReadIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private ProjectRepository projects;

	private Long publicId;

	private Long privateId;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@BeforeEach
	void seed() {
		Project pub = projects.findByName("cm-public").orElseGet(() -> projects.save(new Project("cm-public", null)));
		pub.setVisibility(Visibility.PUBLIC);
		publicId = projects.save(pub).getId();

		Project prv = projects.findByName("cm-private").orElseGet(() -> projects.save(new Project("cm-private", null)));
		prv.setVisibility(Visibility.PRIVATE);
		privateId = projects.save(prv).getId();
	}

	@Test
	void anonymousCanReadPublicButNotPrivate() throws Exception {
		MockMvc mvc = mvc();
		// The board and a public project are reachable WITHOUT a login even in closed
		// mode.
		mvc.perform(get("/")).andExpect(status().isOk());
		mvc.perform(get("/projects/{id}", publicId)).andExpect(status().isOk());
		mvc.perform(get("/api/v1/projects"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("cm-public")))
			.andExpect(content().string(not(containsString("cm-private"))));
		// A private project is hidden (404) — the controller enforces visibility.
		mvc.perform(get("/projects/{id}", privateId)).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/projects/{id}", privateId)).andExpect(status().isNotFound());
	}

	@Test
	void anonymousWritesAndProfileRequireLogin() throws Exception {
		MockMvc mvc = mvc();
		// A management write (with a CSRF token, so this tests the AUTH layer) redirects
		// to login.
		mvc.perform(post("/projects/{id}/members", publicId).with(csrf()).param("username", "x").param("role", "READ"))
			.andExpect(status().is3xxRedirection());
		// /profile still requires a principal.
		mvc.perform(get("/profile")).andExpect(status().is3xxRedirection());
	}

}
