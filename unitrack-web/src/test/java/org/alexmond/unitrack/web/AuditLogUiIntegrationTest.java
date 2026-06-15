package org.alexmond.unitrack.web;

import org.alexmond.unitrack.web.account.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The audit-log viewer is admin-only and renders recorded actions. */
@SpringBootTest
class AuditLogUiIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private AuditService audit;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void anonymousIsSentToLogin() throws Exception {
		mvc().perform(get("/audit")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
	}

	@Test
	@WithMockUser(username = "bob", roles = "USER")
	void nonAdminIsForbidden() throws Exception {
		mvc().perform(get("/audit")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "root", roles = "ADMIN")
	void adminSeesRecordedActions() throws Exception {
		this.audit.record("root", "CREATE_TRIAGE_RULE", "MCP", null, "rule 'audit-ui-probe' -> category 'infra'");
		mvc().perform(get("/audit"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("CREATE_TRIAGE_RULE")))
			.andExpect(content().string(containsString("audit-ui-probe")));
	}

}
