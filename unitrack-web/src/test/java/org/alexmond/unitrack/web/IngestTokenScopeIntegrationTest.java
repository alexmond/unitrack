package org.alexmond.unitrack.web;

import org.alexmond.unitrack.domain.TokenScope;
import org.alexmond.unitrack.domain.User;
import org.alexmond.unitrack.web.account.ApiTokenService;
import org.alexmond.unitrack.web.account.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ingest-scoped tokens may upload but nothing else; full tokens are unrestricted. */
@SpringBootTest
class IngestTokenScopeIntegrationTest {

	private static final byte[] JUNIT = ("<?xml version=\"1.0\"?>"
			+ "<testsuite name=\"com.x.S\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\" time=\"0.01\">"
			+ "<testcase name=\"ok\" classname=\"com.x.S\" time=\"0.01\"/></testsuite>")
		.getBytes();

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserService users;

	@Autowired
	private ApiTokenService tokens;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	private String mint(TokenScope scope) {
		User admin = users.findByUsername("admin").orElseThrow();
		return tokens.create(admin, "scope-" + scope, null, scope).rawToken();
	}

	@Test
	void ingestScopedTokenCanUploadButNotRead() throws Exception {
		MockMvc mvc = mvc();
		String ingestToken = mint(TokenScope.INGEST);
		MockMultipartFile junit = new MockMultipartFile("junit", "TEST.xml", "text/xml", JUNIT);

		// Ingest works with the scoped token.
		mvc.perform(multipart("/api/v1/ingest").file(junit)
			.param("project", "scope-demo")
			.param("commit", "c1")
			.header("Authorization", "Bearer " + ingestToken)).andExpect(status().isCreated());

		// The same token is rejected on any other endpoint (least privilege).
		mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + ingestToken))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + ingestToken))
			.andExpect(status().isForbidden());
	}

	@Test
	void fullTokenIsUnrestricted() throws Exception {
		MockMvc mvc = mvc();
		String fullToken = mint(TokenScope.FULL);
		mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + fullToken)).andExpect(status().isOk());
	}

	@Test
	void actionScopedTokenIsRejectedOutsideMcp() throws Exception {
		MockMvc mvc = mvc();
		String actionToken = mint(TokenScope.ACTION);
		// An ACTION token may only authenticate on the MCP transport; anywhere else is
		// 403.
		mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + actionToken))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + actionToken))
			.andExpect(status().isForbidden());
	}

}
