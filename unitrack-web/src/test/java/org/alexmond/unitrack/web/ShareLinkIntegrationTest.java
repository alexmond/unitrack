package org.alexmond.unitrack.web;

import com.jayway.jsonpath.JsonPath;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.Visibility;
import org.alexmond.unitrack.report.ReportingService;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.alexmond.unitrack.web.account.ShareLinkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
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

@SpringBootTest
class ShareLinkIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private ShareLinkService shareLinks;

	@Autowired
	private ReportingService reporting;

	@Autowired
	private ProjectRepository projects;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	private long ingest(MockMvc mvc, String project) throws Exception {
		byte[] xml = ("<?xml version=\"1.0\"?><testsuite name=\"com.x.ShareSuite\" tests=\"1\" failures=\"0\" "
				+ "errors=\"0\" skipped=\"0\" time=\"0.01\">"
				+ "<testcase name=\"ok\" classname=\"com.x.ShareSuite\" time=\"0.01\"/></testsuite>")
			.getBytes();
		String body = mvc
			.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST-Share.xml", "text/xml", xml))
				.param("project", project)
				.param("branch", "main")
				.param("commit", "cafebabe"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.runId")).longValue();
	}

	@Test
	void shareTokenRendersRunReadOnlyEvenWhenProjectIsPrivate() throws Exception {
		MockMvc mvc = mockMvc();
		long runId = ingest(mvc, "share-private-demo");
		// Lock the project down: anonymous browsing is sent to login (a private run isn't
		// publicly viewable without the share token).
		Project project = projects.findByName("share-private-demo").orElseThrow();
		project.setVisibility(Visibility.PRIVATE);
		projects.save(project);
		mvc.perform(get("/runs/{id}", runId)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));

		TestRun run = reporting.findRun(runId).orElseThrow();
		String token = shareLinks.create(run, null).rawToken();

		// The token is the capability: the read-only view renders despite PRIVATE
		// visibility.
		mvc.perform(get("/share/{token}", token))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("share-private-demo")))
			.andExpect(content().string(containsString("com.x.ShareSuite")))
			.andExpect(content().string(containsString("Shared read-only view")));
	}

	@Test
	void revokedAndUnknownTokensAreNotFound() throws Exception {
		MockMvc mvc = mockMvc();
		long runId = ingest(mvc, "share-revoke-demo");
		TestRun run = reporting.findRun(runId).orElseThrow();
		ShareLinkService.Minted minted = shareLinks.create(run, null);

		mvc.perform(get("/share/{token}", minted.rawToken())).andExpect(status().isOk());

		shareLinks.revoke(minted.link().getId());
		mvc.perform(get("/share/{token}", minted.rawToken())).andExpect(status().isNotFound());
		mvc.perform(get("/share/{token}", "sh_does-not-exist")).andExpect(status().isNotFound());
	}

}
