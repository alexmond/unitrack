package org.alexmond.unitrack.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * In closed mode the login page's static assets (CSS/JS/webjars) must stay public —
 * otherwise the form renders unstyled and the blocked {@code /js/live.js} fetch becomes
 * the saved post-login redirect target. Guards that regression.
 */
@SpringBootTest(properties = "unitrack.security.open-mode=false")
class ClosedModeStaticAssetsTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	void staticAssetsAreServedAnonymouslyWhileProtectedPagesRedirect() throws Exception {
		MockMvc mvc = mvc();

		// Closed mode is active: a protected UI page redirects anonymous users to login.
		mvc.perform(get("/profile")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));

		// Static assets are public — served, not redirected to login.
		mvc.perform(get("/js/live.js")).andExpect(status().isOk());
		mvc.perform(get("/css/app.css")).andExpect(status().isOk());
	}

}
