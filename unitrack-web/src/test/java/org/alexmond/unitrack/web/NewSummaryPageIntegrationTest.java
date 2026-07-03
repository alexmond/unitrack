package org.alexmond.unitrack.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The "New summary" page is login-only even in open mode: anonymous visitors are
 * redirected to login, a signed-in user gets the cross-project rollup (only the projects
 * they may read — test config makes ingested projects PUBLIC).
 */
@SpringBootTest
class NewSummaryPageIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void anonymousIsRedirectedToLogin() throws Exception {
		mvc().perform(get("/new-summary")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
	}

	@Test
	void signedInUserSeesThePage() throws Exception {
		mvc().perform(get("/new-summary").with(user("alice"))).andExpect(status().isOk());
	}

	@Test
	void summaryRendersIngestedProjectAndKpis() throws Exception {
		MockMvc mvc = mvc();
		byte[] junit = getClass().getResourceAsStream("/samples/surefire-sample.xml").readAllBytes();
		byte[] jacoco = getClass().getResourceAsStream("/samples/jacoco-sample.xml").readAllBytes();
		mvc.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST-Calc.xml", "text/xml", junit))
			.file(new MockMultipartFile("jacoco", "jacoco.xml", "text/xml", jacoco))
			.param("project", "summary-demo")
			.param("branch", "main")
			.param("commit", "abcdef1234567890")
			.param("buildName", "1")).andExpect(status().isCreated());

		// Signed in, the rollup lists the project and renders the KPI strip.
		mvc.perform(get("/new-summary").with(user("alice")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("summary-demo")))
			.andExpect(content().string(containsString("Failing gates")))
			.andExpect(content().string(containsString("Avg coverage")));
	}

}
