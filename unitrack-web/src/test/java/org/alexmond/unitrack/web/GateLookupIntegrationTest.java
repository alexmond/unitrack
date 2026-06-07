package org.alexmond.unitrack.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class GateLookupIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private static byte[] junit(boolean failing) {
		String tc = failing
				? "<testcase name=\"a\" classname=\"com.x.G\" time=\"0.01\">"
						+ "<failure message=\"boom\" type=\"java.lang.AssertionError\">trace</failure></testcase>"
				: "<testcase name=\"a\" classname=\"com.x.G\" time=\"0.01\"/>";
		return ("<?xml version=\"1.0\"?><testsuite name=\"com.x.G\" tests=\"1\" failures=\"" + (failing ? 1 : 0)
				+ "\" errors=\"0\" skipped=\"0\" time=\"0.01\">" + tc + "</testsuite>")
			.getBytes();
	}

	private void ingest(MockMvc mvc, String project, String commit, boolean failing) throws Exception {
		mvc.perform(multipart("/api/v1/ingest")
			.file(new MockMultipartFile("junit", "TEST-G.xml", "text/xml", junit(failing)))
			.param("project", project)
			.param("branch", "main")
			.param("commit", commit)).andExpect(status().isCreated());
	}

	@Test
	void looksUpGateByCommit() throws Exception {
		MockMvc mvc = mockMvc();
		ingest(mvc, "gate-lookup", "basecommit", false); // baseline passes
		ingest(mvc, "gate-lookup", "badcommit", true); // later commit introduces a
														// failure

		// Passing commit -> gate passed.
		mvc.perform(get("/api/v1/gate").param("project", "gate-lookup").param("commit", "basecommit"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.passed").value(true))
			.andExpect(jsonPath("$.status").value("PASSED"))
			.andExpect(jsonPath("$.commit").value("basecommit"));

		// Regressing commit -> gate failed, with the failing rule reported.
		mvc.perform(get("/api/v1/gate").param("project", "gate-lookup").param("commit", "badcommit"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.passed").value(false))
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.rules[0].name").value("new-failures"));
	}

	@Test
	void looksUpGateByBranch() throws Exception {
		MockMvc mvc = mockMvc();
		ingest(mvc, "gate-branch", "c1", false);

		mvc.perform(get("/api/v1/gate").param("project", "gate-branch").param("branch", "main"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.branch").value("main"))
			.andExpect(jsonPath("$.runPath").exists());
	}

	@Test
	void unknownProjectIsNotFound() throws Exception {
		mockMvc().perform(get("/api/v1/gate").param("project", "nope").param("commit", "x"))
			.andExpect(status().isNotFound());
	}

	@Test
	void missingCommitAndBranchIsBadRequest() throws Exception {
		mockMvc().perform(get("/api/v1/gate").param("project", "anything")).andExpect(status().isBadRequest());
	}

}
