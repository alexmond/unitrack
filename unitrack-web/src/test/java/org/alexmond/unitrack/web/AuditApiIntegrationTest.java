package org.alexmond.unitrack.web;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Observability #2: ingest records a lifecycle audit entry, queryable via the admin-only
 * API.
 */
@SpringBootTest
class AuditApiIntegrationTest {

	private static final byte[] JUNIT = ("<?xml version=\"1.0\"?>"
			+ "<testsuite name=\"com.x.S\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\" time=\"0.01\">"
			+ "<testcase name=\"ok\" classname=\"com.x.S\" time=\"0.01\"/></testsuite>")
		.getBytes();

	@Autowired
	private WebApplicationContext context;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	@WithMockUser(username = "admin", roles = "ADMIN")
	void ingestIsAuditedAndQueryableByAdmin() throws Exception {
		MockMvc mvc = mvc();
		mvc.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST.xml", "text/xml", JUNIT))
			.param("project", "audit-api-demo")
			.param("commit", "c1")).andExpect(status().isCreated());

		mvc.perform(get("/api/v1/audit"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("RUN_INGESTED")));
	}

	@Test
	void anonymousIsUnauthorized() throws Exception {
		mvc().perform(get("/api/v1/audit")).andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(username = "bob", roles = "USER")
	void nonAdminIsForbidden() throws Exception {
		mvc().perform(get("/api/v1/audit")).andExpect(status().isForbidden());
	}

}
