package org.alexmond.unitrack.web;

import org.alexmond.unitrack.domain.User;
import org.alexmond.unitrack.web.account.ApiTokenService;
import org.alexmond.unitrack.web.account.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuthIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserService users;

	@Autowired
	private ApiTokenService tokens;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void apiTokenAuthenticatesAndRevocationDeniesAccess() throws Exception {
		MockMvc mvc = mvc();
		User admin = users.findByUsername("admin").orElseThrow();
		ApiTokenService.Minted minted = tokens.create(admin, "ci", null, org.alexmond.unitrack.domain.TokenScope.FULL);
		String token = minted.rawToken();

		// No token -> 401 (API entry point), not a login redirect.
		mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());

		// Valid token -> authenticated as the owner.
		mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("admin"))
			.andExpect(jsonPath("$.role").value("ADMIN"));

		// Revoked token -> 401.
		tokens.revoke(minted.token().getId(), admin.getId());
		mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
	}

	@Test
	void formLoginGrantsAccessToProfileWhileAnonymousIsRedirected() throws Exception {
		MockMvc mvc = mvc();

		// Anonymous hitting a protected UI page is redirected to login (even in open
		// mode).
		mvc.perform(get("/profile")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));

		// Seeded admin can log in (password from unitrack.security.admin-password in test
		// config). POST to the form-login processing URL with a CSRF token (CSRF is on).
		mvc.perform(post("/login").with(csrf()).param("username", "admin").param("password", "testadmin"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/"));
	}

	@Test
	void openModeKeepsDataApisAccessibleWithoutAuth() throws Exception {
		// Open mode (default): read APIs stay public so CI/uploader keep working.
		mvc().perform(get("/api/v1/projects")).andExpect(status().isOk());
	}

}
