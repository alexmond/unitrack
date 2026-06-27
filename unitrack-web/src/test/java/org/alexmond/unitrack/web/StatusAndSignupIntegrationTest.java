package org.alexmond.unitrack.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Status page renders as HTML, and signup is off by default. */
@SpringBootTest
class StatusAndSignupIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void statusPageRendersHealthAsHtml() throws Exception {
		mvc().perform(get("/status"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("System status")))
			.andExpect(content().string(containsString("Components")))
			// H2 datasource health is contributed as the "db" component.
			.andExpect(content().string(containsString("db")));
	}

	@Test
	void signupIsDisabledByDefault() throws Exception {
		MockMvc mvc = mvc();
		mvc.perform(get("/signup")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
		mvc.perform(post("/signup").with(csrf()).param("username", "nope").param("password", "password123"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

}
