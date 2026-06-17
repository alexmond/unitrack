package org.alexmond.unitrack.web.ops;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Observability #5: the admin-only /ops dashboard renders stats + recent failures. */
@SpringBootTest
class OpsPageIntegrationTest {

	private static final byte[] FAILING = ("<?xml version=\"1.0\"?>"
			+ "<testsuite name=\"com.x.S\" tests=\"1\" failures=\"1\" errors=\"0\" skipped=\"0\" time=\"0.01\">"
			+ "<testcase name=\"boom\" classname=\"com.x.S\" time=\"0.01\">"
			+ "<failure message=\"x\" type=\"java.lang.AssertionError\">trace</failure></testcase></testsuite>")
		.getBytes();

	@Autowired
	private WebApplicationContext context;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	@WithMockUser(username = "admin", roles = "ADMIN")
	void opsPageShowsStatsAndRecentFailure() throws Exception {
		MockMvc mvc = mvc();
		mvc.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST.xml", "text/xml", FAILING))
			.param("project", "ops-fail-demo")
			.param("commit", "deadbee")).andExpect(status().isCreated());

		mvc.perform(get("/ops"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Operations")))
			.andExpect(content().string(containsString("Live clients")))
			.andExpect(content().string(containsString("ops-fail-demo")));

		// The HTMX-polled fragment also renders the stats + failures.
		mvc.perform(get("/ops/stats"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Live clients")))
			.andExpect(content().string(containsString("ops-fail-demo")));
	}

	@Test
	void anonymousIsSentToLogin() throws Exception {
		mvc().perform(get("/ops")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
	}

	@Test
	@WithMockUser(username = "bob", roles = "USER")
	void nonAdminIsForbidden() throws Exception {
		mvc().perform(get("/ops")).andExpect(status().isForbidden());
	}

}
