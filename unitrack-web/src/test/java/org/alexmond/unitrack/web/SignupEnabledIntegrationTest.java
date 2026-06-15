package org.alexmond.unitrack.web;

import org.alexmond.unitrack.domain.Role;
import org.alexmond.unitrack.domain.User;
import org.alexmond.unitrack.web.account.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Conditional signup, exercised with {@code unitrack.security.signup-enabled=true}. */
@SpringBootTest(properties = "unitrack.security.signup-enabled=true")
class SignupEnabledIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserService users;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void signupFormIsServedWhenEnabled() throws Exception {
		mvc().perform(get("/signup"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Create account")));
	}

	@Test
	void signupCreatesUserAndLogsThemIn() throws Exception {
		mvc()
			.perform(post("/signup").param("username", "su-alice")
				.param("email", "su-alice@example.com")
				.param("password", "password123"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/profile"));

		User created = users.findByUsername("su-alice").orElseThrow();
		assertThat(created.getRole()).isEqualTo(Role.USER);
		assertThat(created.getEmail()).isEqualTo("su-alice@example.com");
	}

	@Test
	void shortPasswordIsRejected() throws Exception {
		mvc().perform(post("/signup").param("username", "su-bob").param("password", "short"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("at least 8 characters")));
		assertThat(users.findByUsername("su-bob")).isEmpty();
	}

}
