package org.alexmond.unitrack.web;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.ProjectRole;
import org.alexmond.unitrack.domain.Role;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.Visibility;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.alexmond.unitrack.web.account.MembershipService;
import org.alexmond.unitrack.web.account.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public/private project visibility: enforced on reads, owner-gated on writes. */
@SpringBootTest
class ProjectVisibilityIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private ProjectRepository projects;

	@Autowired
	private TestRunRepository runs;

	@Autowired
	private UserService users;

	@Autowired
	private MembershipService membership;

	private Long publicId;

	private Long privateId;

	private Long privateRunId;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@BeforeEach
	void seed() {
		if (users.findByUsername("vis-alice").isEmpty()) {
			users.create("vis-alice", "Alice", "alice@example.com", "password123", Role.USER);
		}
		if (users.findByUsername("vis-bob").isEmpty()) {
			users.create("vis-bob", "Bob", "bob@example.com", "password123", Role.USER);
		}
		Project pub = projects.findByName("vis-public-proj")
			.orElseGet(() -> projects.save(new Project("vis-public-proj", null)));
		pub.setVisibility(Visibility.PUBLIC);
		publicId = projects.save(pub).getId();

		Project prv = projects.findByName("vis-private-proj")
			.orElseGet(() -> projects.save(new Project("vis-private-proj", null)));
		prv.setVisibility(Visibility.PRIVATE);
		projects.save(prv);
		privateId = prv.getId();
		membership.addOrUpdate(privateId, "vis-alice", ProjectRole.READ);
		privateRunId = runs.save(new TestRun(prv, "main", "default", "sha-vis-1", null, null)).getId();
	}

	@Test
	void anonymousSeesPublicButNotPrivate() throws Exception {
		MockMvc mvc = mvc();
		mvc.perform(get("/api/v1/projects"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("vis-public-proj")))
			.andExpect(content().string(not(containsString("vis-private-proj"))));
		mvc.perform(get("/projects/{id}", publicId)).andExpect(status().isOk());
		mvc.perform(get("/projects/{id}", privateId)).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/projects/{id}", privateId)).andExpect(status().isNotFound());
		mvc.perform(get("/runs/{id}", privateRunId)).andExpect(status().isNotFound());
		mvc.perform(get("/")).andExpect(content().string(not(containsString("vis-private-proj"))));
	}

	@Test
	void memberAndAdminCanReadPrivate() throws Exception {
		MockMvc mvc = mvc();
		mvc.perform(get("/projects/{id}", privateId).with(user("vis-alice"))).andExpect(status().isOk());
		mvc.perform(get("/runs/{id}", privateRunId).with(user("vis-alice"))).andExpect(status().isOk());
		mvc.perform(get("/projects/{id}", privateId).with(user("admin"))).andExpect(status().isOk());
		// A non-member still can't see it.
		mvc.perform(get("/projects/{id}", privateId).with(user("vis-bob"))).andExpect(status().isNotFound());
	}

	@Test
	void visibilityToggleIsOwnerOnly() throws Exception {
		MockMvc mvc = mvc();
		// READ member is not an owner -> forbidden.
		mvc.perform(post("/projects/{id}/visibility", privateId).with(csrf())
			.param("visibility", "PUBLIC")
			.with(user("vis-alice"))).andExpect(status().isForbidden());
		// Admin can change it.
		mvc.perform(post("/projects/{id}/visibility", privateId).with(csrf())
			.param("visibility", "PUBLIC")
			.with(user("admin"))).andExpect(status().is3xxRedirection());
	}

}
