package org.alexmond.unitrack.web;

import com.jayway.jsonpath.JsonPath;
import org.alexmond.unitrack.report.ProjectSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ProjectSettingsIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private ProjectSettingsService settings;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	private static final byte[] JUNIT = ("<?xml version=\"1.0\"?><testsuite name=\"S\" tests=\"1\" failures=\"0\" "
			+ "errors=\"0\" skipped=\"0\" time=\"0.01\"><testcase name=\"t\" classname=\"S\" time=\"0.01\"/></testsuite>")
		.getBytes();

	// JaCoCo report with 1 of 2 lines covered -> 50% line coverage.
	private static final byte[] JACOCO = ("<?xml version=\"1.0\"?><report name=\"r\">"
			+ "<counter type=\"LINE\" missed=\"1\" covered=\"1\"/>"
			+ "<package name=\"p\"><sourcefile name=\"F.java\"><counter type=\"LINE\" missed=\"1\" covered=\"1\"/>"
			+ "</sourcefile></package></report>")
		.getBytes();

	private long[] ingest(MockMvc mvc, String project) throws Exception {
		String body = mvc
			.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST-S.xml", "text/xml", JUNIT))
				.file(new MockMultipartFile("jacoco", "jacoco.xml", "text/xml", JACOCO))
				.param("project", project)
				.param("branch", "main")
				.param("commit", "c1"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return new long[] { ((Number) JsonPath.read(body, "$.projectId")).longValue(),
				((Number) JsonPath.read(body, "$.runId")).longValue() };
	}

	@Test
	void perProjectMinCoverageOverridesGlobalDefault() throws Exception {
		MockMvc mvc = mvc();
		long[] ids = ingest(mvc, "settings-demo");
		long projectId = ids[0];
		long runId = ids[1];

		// No override and no global minimum -> the min-coverage rule is absent, gate
		// passes.
		mvc.perform(get("/api/v1/runs/{id}/quality-gate", runId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.passed").value(true))
			.andExpect(jsonPath("$.rules[*].name", not(hasItem("min-coverage"))));

		// Set a per-project minimum of 80% -> the run's 50% now fails the gate.
		settings.save(projectId, null, 80.0, null, null, null, null, null);

		mvc.perform(get("/api/v1/runs/{id}/quality-gate", runId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.passed").value(false))
			.andExpect(jsonPath("$.rules[*].name", hasItem("min-coverage")));
	}

	@Test
	void settingsPageRequiresLogin() throws Exception {
		MockMvc mvc = mvc();
		long projectId = ingest(mvc, "settings-auth")[0];
		mvc.perform(get("/projects/{id}/settings", projectId)).andExpect(redirectedUrl("/login"));
	}

	@Test
	@WithMockUser("admin")
	void settingsPageRendersAndSavesForAuthenticatedUser() throws Exception {
		MockMvc mvc = mvc();
		long projectId = ingest(mvc, "settings-ui")[0];

		mvc.perform(get("/projects/{id}/settings", projectId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Quality gate")))
			.andExpect(content().string(containsString("GitHub")));

		mvc.perform(post("/projects/{id}/settings", projectId).param("baseBranch", "release")
			.param("minLineCoverage", "75")
			.param("maxCoverageDropPct", "")
			.param("failOnNewFailures", "false")
			.param("ghEnabled", "true")
			.param("ghContext", "ci/coverage")
			.param("ghPrComment", "")).andExpect(status().is3xxRedirection());

		assertThat(settings.gateConfig(projectId).baseBranch()).isEqualTo("release");
		assertThat(settings.gateConfig(projectId).minLineCoverage()).isEqualTo(75.0);
	}

}
