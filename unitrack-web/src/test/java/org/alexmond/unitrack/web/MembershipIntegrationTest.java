package org.alexmond.unitrack.web;

import com.jayway.jsonpath.JsonPath;
import org.alexmond.unitrack.domain.ProjectRole;
import org.alexmond.unitrack.domain.Role;
import org.alexmond.unitrack.domain.User;
import org.alexmond.unitrack.repository.UserRepository;
import org.alexmond.unitrack.web.account.MembershipService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class MembershipIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private MembershipService membership;

	@Autowired
	private UserRepository users;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	private static final byte[] JUNIT = ("<?xml version=\"1.0\"?><testsuite name=\"S\" tests=\"1\" failures=\"0\" "
			+ "errors=\"0\" skipped=\"0\" time=\"0.01\"><testcase name=\"t\" classname=\"S\" time=\"0.01\"/></testsuite>")
		.getBytes();

	private long ingest(MockMvc mvc, String project) throws Exception {
		String body = mvc
			.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST-S.xml", "text/xml", JUNIT))
				.param("project", project)
				.param("commit", "c1"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.projectId")).longValue();
	}

	private void user(String username) {
		if (this.users.findByUsername(username).isEmpty()) {
			this.users.save(new User(username, username, username + "@x", "x", Role.USER));
		}
	}

	@Test
	void rolesGovernWriteAndManage() throws Exception {
		long projectId = ingest(mvc(), "rbac-roles");
		user("alice");

		// Global admin needs no membership.
		assertThat(membership.canWrite("admin", projectId)).isTrue();
		assertThat(membership.canManage("admin", projectId)).isTrue();

		// Plain user with no membership: nothing.
		assertThat(membership.canWrite("alice", projectId)).isFalse();

		// WRITE grants write but not manage.
		membership.addOrUpdate(projectId, "alice", ProjectRole.WRITE);
		assertThat(membership.canWrite("alice", projectId)).isTrue();
		assertThat(membership.canManage("alice", projectId)).isFalse();
		assertThat(membership.members(projectId)).anyMatch((m) -> m.username().equals("alice"));

		// Promote to OWNER -> can manage too.
		membership.addOrUpdate(projectId, "alice", ProjectRole.OWNER);
		assertThat(membership.canManage("alice", projectId)).isTrue();

		// Remove -> access gone.
		long mid = membership.members(projectId).get(0).id();
		membership.remove(mid);
		assertThat(membership.canWrite("alice", projectId)).isFalse();
	}

	@Test
	@WithMockUser("admin")
	void adminManagesMembersViaPage() throws Exception {
		MockMvc mvc = mvc();
		long projectId = ingest(mvc, "rbac-page");
		user("bob");

		mvc.perform(get("/projects/{id}/members", projectId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("members")));

		mvc.perform(
				post("/projects/{id}/members", projectId).with(csrf()).param("username", "bob").param("role", "WRITE"))
			.andExpect(status().is3xxRedirection());
		assertThat(membership.canWrite("bob", projectId)).isTrue();

		// Unknown user -> redirect with error, no membership created.
		mvc.perform(
				post("/projects/{id}/members", projectId).with(csrf()).param("username", "ghost").param("role", "READ"))
			.andExpect(status().is3xxRedirection());
	}

	@Test
	@WithMockUser("carol")
	void nonOwnerForbiddenFromMembersAndWrites() throws Exception {
		MockMvc mvc = mvc();
		long projectId = ingest(mvc, "rbac-deny");
		user("carol");

		mvc.perform(get("/projects/{id}/members", projectId)).andExpect(status().isForbidden());
		mvc.perform(post("/projects/{id}/settings", projectId).with(csrf()).param("minLineCoverage", "70"))
			.andExpect(status().isForbidden());
	}

}
