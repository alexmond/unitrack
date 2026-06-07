package org.alexmond.unitrack.web;

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

@SpringBootTest(properties = "unitrack.security.require-ingest-token=true")
class SecureIngestIntegrationTest {

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

	@Test
	void ingestRequiresTokenButReadsStayOpen() throws Exception {
		MockMvc mvc = mvc();
		MockMultipartFile junit = new MockMultipartFile("junit", "TEST.xml", "text/xml", JUNIT);

		// No token -> 401.
		mvc.perform(multipart("/api/v1/ingest").file(junit).param("project", "sec-demo"))
			.andExpect(status().isUnauthorized());

		// Valid token -> ingested.
		User admin = users.findByUsername("admin").orElseThrow();
		String token = tokens.create(admin, "ci", null).rawToken();
		mvc.perform(multipart("/api/v1/ingest").file(junit)
			.param("project", "sec-demo")
			.param("commit", "c1")
			.header("Authorization", "Bearer " + token)).andExpect(status().isCreated());

		// Reads remain public in open mode.
		mvc.perform(get("/api/v1/projects")).andExpect(status().isOk());
	}

}
