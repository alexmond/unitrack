package org.alexmond.unitrack.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The reconciled Overview — now the project page at {@code /projects/{id}}. Verifies it
 * synthesizes the four aspects into a verdict + aspect strip, degrades on the Load aspect
 * (no perf data), routes into the tabs, and that the old {@code /overview} preview URL
 * redirects to it.
 */
@SpringBootTest
class NewOverviewPageIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private org.alexmond.unitrack.repository.ProjectRepository projects;

	private static byte[] junit(boolean pass) {
		String failures = pass ? "0" : "1";
		String caseBody = pass ? "<testcase name=\"t\" classname=\"com.x.X\" time=\"0.1\"/>"
				: "<testcase name=\"t\" classname=\"com.x.X\" time=\"0.1\"><failure message=\"boom\">x</failure></testcase>";
		return ("<?xml version=\"1.0\"?><testsuite name=\"s\" tests=\"1\" failures=\"" + failures + "\" errors=\"0\" "
				+ "skipped=\"0\" time=\"0.1\">" + caseBody + "</testsuite>")
			.getBytes();
	}

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private long ingest(MockMvc mvc, String project, String commit, boolean pass) throws Exception {
		String body = mvc
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("junit", "TEST.xml", "text/xml", junit(pass)))
				.param("project", project)
				.param("branch", "main")
				.param("commit", commit))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.projectId")).longValue();
	}

	@Test
	void recentRunCommitLinksToRepoHostWhenRepoUrlSet() throws Exception {
		MockMvc mvc = mockMvc();
		long id = ingest(mvc, "overview-commitlink", "abcdef1", true);
		// Give the project a repo so the commit column becomes a GitHub /commit/<sha>
		// link.
		var p = projects.findById(id).orElseThrow();
		p.setRepoUrl("https://github.com/acme/demo.git");
		projects.save(p);
		mvc.perform(get("/projects/{id}", id))
			.andExpect(status().isOk())
			// .git stripped, /commit/<full sha> appended by repoCommitBase
			.andExpect(content().string(containsString("https://github.com/acme/demo/commit/abcdef1")));
	}

	@Test
	void healthyProjectRendersVerdictAspectStripAndTrend() throws Exception {
		MockMvc mvc = mockMvc();
		long id = ingest(mvc, "overview-green", "aaa1", true);
		ingest(mvc, "overview-green", "bbb2", true);

		mvc.perform(get("/projects/{id}", id))
			.andExpect(status().isOk())
			// Verdict band.
			.andExpect(content().string(containsString("Healthy")))
			// Aspect strip — one card per aspect (Load omitted: no perf data).
			.andExpect(content().string(containsString("Tests")))
			.andExpect(content().string(containsString("Coverage")))
			.andExpect(content().string(containsString("Test timing")))
			// Routes into the tabs.
			.andExpect(content().string(containsString("/projects/" + id + "/tests")))
			.andExpect(content().string(containsString("/projects/" + id + "/coverage")))
			.andExpect(content().string(containsString("/projects/" + id + "/performance")))
			// The multi-series trend (>=2 runs) and demoted runs table.
			.andExpect(content().string(containsString("Health over time")))
			.andExpect(content().string(containsString("Recent runs")))
			// Load card is hidden when the project has no perf data (its rocket icon is
			// unique
			// to that card; note "/perf" is a prefix of the Timing card's "/performance"
			// route).
			.andExpect(content().string(org.hamcrest.Matchers.not(containsString("bi-rocket-takeoff"))));
	}

	@Test
	void failingProjectReadsAsFailing() throws Exception {
		MockMvc mvc = mockMvc();
		long id = ingest(mvc, "overview-red", "ccc1", false);

		mvc.perform(get("/projects/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Failing")))
			.andExpect(content().string(containsString("test failing")));
	}

	@Test
	void oldPreviewUrlRedirectsToTheProjectPage() throws Exception {
		MockMvc mvc = mockMvc();
		long id = ingest(mvc, "overview-link", "ddd1", true);

		mvc.perform(get("/projects/{id}/overview", id))
			.andExpect(status().is3xxRedirection())
			.andExpect(
					org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/projects/" + id));
	}

}
