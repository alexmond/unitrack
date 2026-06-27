package org.alexmond.unitrack.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Self-service signup is rate-limited per client IP. */
@SpringBootTest(
		properties = { "unitrack.security.signup-enabled=true", "unitrack.security.signup-rate-limit-per-hour=2" })
class SignupRateLimitIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	void blocksAfterTheHourlyLimit() throws Exception {
		MockMvc mvc = mvc();
		mvc.perform(post("/signup").with(csrf()).param("username", "su-rl-1").param("password", "password123"))
			.andExpect(status().is3xxRedirection());
		mvc.perform(post("/signup").with(csrf()).param("username", "su-rl-2").param("password", "password123"))
			.andExpect(status().is3xxRedirection());
		mvc.perform(post("/signup").with(csrf()).param("username", "su-rl-3").param("password", "password123"))
			.andExpect(status().isTooManyRequests());
	}

}
