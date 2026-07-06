package org.alexmond.unitrack.web;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The reconciled Tests page ({@code /projects/{id}/tests}, epic #390) is the canonical
 * Tests tab: KPI tiles, trend, by-module breakdown, an all-tests roster, and the folded
 * Flaky and Failure-clusters sections. It is public like the other analytics tabs, and
 * the old {@code /flaky}, {@code /clusters}, and {@code /new-tests} URLs redirect to it.
 */
@SpringBootTest
class TestsPageIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	private static byte[] junitXml() {
		return ("<?xml version=\"1.0\"?><testsuite name=\"com.example.MyClass\" tests=\"1\" failures=\"0\""
				+ " errors=\"0\" skipped=\"0\" time=\"0.02\">"
				+ "<testcase name=\"myTest\" classname=\"com.example.MyClass\" time=\"0.02\"/></testsuite>")
			.getBytes();
	}

	private long ingest(MockMvc mvc, String project) throws Exception {
		return ingest(mvc, project, "main", "sha1");
	}

	private static byte[] junitCase(String name) {
		return ("<?xml version=\"1.0\"?><testsuite name=\"s\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\""
				+ " time=\"0.1\"><testcase name=\"" + name + "\" classname=\"com.x.M\" time=\"0.1\"/></testsuite>")
			.getBytes();
	}

	/**
	 * A single run carrying two modules: two sharded uploads with the same {@code runKey}
	 * (so they merge into one run), each tagged with a different explicit {@code module}
	 * (#393). Yields a By-module breakdown ({@code svc}, {@code web}).
	 */
	private long ingestMultiModule(MockMvc mvc, String project) throws Exception {
		ingestShard(mvc, project, "svc", "svcTest");
		return ingestShard(mvc, project, "web", "webTest");
	}

	private long ingestShard(MockMvc mvc, String project, String module, String caseName) throws Exception {
		String body = mvc
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("junit", "TEST.xml", "text/xml", junitCase(caseName)))
				.param("project", project)
				.param("branch", "main")
				.param("commit", "shard-commit")
				.param("runKey", "build-mm")
				.param("module", module))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.projectId")).longValue();
	}

	private long ingest(MockMvc mvc, String project, String branch, String commit) throws Exception {
		String body = mvc
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("junit", "TEST-MyClass.xml", "text/xml", junitXml()))
				.param("project", project)
				.param("branch", branch)
				.param("commit", commit))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.projectId")).longValue();
	}

	/**
	 * A project with more than one branch renders the shared branch scope dropdown
	 * (view.ScopeBar / fragments/common :: scopeBar, #431) — "All branches" plus each
	 * branch — and scoping the tab by {@code ?branch=} stays a 200 (non-breaking default
	 * is all branches).
	 */
	@Test
	void multiBranchProjectShowsBranchScopeDropdown() throws Exception {
		MockMvc mvc = mvc();
		long projectId = ingest(mvc, "tests-branch-scope", "main", "sha-main");
		ingest(mvc, "tests-branch-scope", "feature/x", "sha-feat");

		mvc.perform(get("/projects/{id}/tests", projectId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("data-scope=\"branch\"")))
			.andExpect(content().string(containsString("All branches")))
			.andExpect(content().string(containsString("feature/x")));

		mvc.perform(get("/projects/{id}/tests", projectId).param("branch", "main")).andExpect(status().isOk());
	}

	@Test
	void publicPageShowsFoldedFlakyAndClusterSections() throws Exception {
		MockMvc mvc = mvc();
		long projectId = ingest(mvc, "tests-page-public");

		// No authenticated user — the analytics page is public.
		mvc.perform(get("/projects/{id}/tests", projectId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("All tests")))
			.andExpect(content().string(containsString("id=\"flaky-section\"")))
			.andExpect(content().string(containsString("id=\"clusters-section\"")))
			// With runs present the empty state must NOT render. Guards a Thymeleaf
			// precedence trap: th:replace (100) runs before th:unless (300) on the same
			// element, so the empty-state guard must live on a wrapper, not the replaced
			// div.
			.andExpect(content().string(not(containsString("No test runs yet"))));
	}

	/**
	 * An unknown {@code module} scope falls back to "all tests" (full roster, not
	 * module-scoped) rather than erroring — the page is never a dead end.
	 */
	@Test
	void unknownModuleFallsBackToAllTests() throws Exception {
		MockMvc mvc = mvc();
		long projectId = ingest(mvc, "tests-page-module");

		mvc.perform(get("/projects/{id}/tests", projectId).param("module", "does-not-exist"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("All tests")))
			// Fallback isn't scoped: the bogus module must not surface (e.g. as a
			// breadcrumb crumb).
			.andExpect(content().string(not(containsString("does-not-exist"))));
	}

	/**
	 * The all-modules page shows the by-module picker, and its rows link to each module's
	 * dedicated page ({@code /tests/module/{module}}), not the old {@code ?module=}
	 * scope.
	 */
	@Test
	void modulePickerLinksToDedicatedModulePages() throws Exception {
		MockMvc mvc = mvc();
		long projectId = ingestMultiModule(mvc, "tests-modpick");

		mvc.perform(get("/projects/{id}/tests", projectId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Tests by module")))
			.andExpect(content().string(containsString("/projects/" + projectId + "/tests/module/")));
	}

	/**
	 * A module's dedicated page is module-filtered (the roster is still there) and drops
	 * the module picker ("Tests by module") — you're already in the module.
	 */
	@Test
	void modulePageIsScopedAndDropsThePicker() throws Exception {
		MockMvc mvc = mvc();
		long projectId = ingestMultiModule(mvc, "tests-modpage");

		// Discover a real module URL from the picker, then follow it (robust to naming).
		String all = mvc.perform(get("/projects/{id}/tests", projectId)).andReturn().getResponse().getContentAsString();
		Matcher m = Pattern.compile("/projects/" + projectId + "/tests/module/[\\w.%-]+").matcher(all);
		assertThat(m.find()).as("a dedicated module link on the all-modules page").isTrue();

		mvc.perform(get(m.group()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("All tests")))
			.andExpect(content().string(not(containsString("Tests by module"))));
	}

	/**
	 * The folded-away tabs' URLs redirect to the Tests page so bookmarks keep working.
	 */
	@Test
	void oldTabUrlsRedirectToTests() throws Exception {
		MockMvc mvc = mvc();
		long projectId = ingest(mvc, "tests-page-redirects");

		mvc.perform(get("/projects/{id}/new-tests", projectId))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/projects/" + projectId + "/tests"));
		mvc.perform(get("/projects/{id}/flaky", projectId))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/projects/" + projectId + "/tests#flaky-section"));
		mvc.perform(get("/projects/{id}/clusters", projectId))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/projects/" + projectId + "/tests#clusters-section"));
	}

}
