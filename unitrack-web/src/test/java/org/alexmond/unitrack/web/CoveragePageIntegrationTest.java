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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class CoveragePageIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private static final byte[] JUNIT = ("<?xml version=\"1.0\"?><testsuite name=\"s\" tests=\"1\" failures=\"0\" "
			+ "errors=\"0\" skipped=\"0\" time=\"0.1\"><testcase name=\"t\" classname=\"com.x.X\" time=\"0.1\"/>"
			+ "</testsuite>")
		.getBytes();

	// Two packages, one well-covered (Good.java 95%) and one poorly (Bad.java 40%);
	// report-level counters drive the headline %.
	private static final byte[] JACOCO = ("<?xml version=\"1.0\"?><report name=\"r\">"
			+ "<package name=\"com/acme/svc\"><sourcefile name=\"Good.java\">"
			+ "<counter type=\"LINE\" missed=\"5\" covered=\"95\"/></sourcefile></package>"
			+ "<package name=\"com/acme/web\"><sourcefile name=\"Bad.java\">"
			+ "<counter type=\"LINE\" missed=\"60\" covered=\"40\"/></sourcefile></package>"
			+ "<counter type=\"LINE\" missed=\"65\" covered=\"135\"/>"
			+ "<counter type=\"BRANCH\" missed=\"20\" covered=\"80\"/>"
			+ "<counter type=\"INSTRUCTION\" missed=\"100\" covered=\"400\"/>"
			+ "<counter type=\"METHOD\" missed=\"5\" covered=\"45\"/></report>")
		.getBytes();

	private long ingest(MockMvc mvc, String project, boolean withCoverage) throws Exception {
		var req = multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST.xml", "text/xml", JUNIT))
			.param("project", project)
			.param("branch", "main")
			.param("commit", "deadbee");
		if (withCoverage) {
			req = req.file(new MockMultipartFile("jacoco", "coverage.xml", "text/xml", JACOCO));
		}
		String body = mvc.perform(req).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.projectId")).longValue();
	}

	@Test
	void coveragePageShowsPackagesAndWorstFiles() throws Exception {
		MockMvc mvc = mockMvc();
		long projectId = ingest(mvc, "cov-page", true);

		mvc.perform(get("/projects/{id}/coverage", projectId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("By package")))
			.andExpect(content().string(containsString("com/acme/svc")))
			.andExpect(content().string(containsString("com/acme/web")))
			.andExpect(content().string(containsString("Worst-covered files")))
			.andExpect(content().string(containsString("Bad.java")))
			.andExpect(content().string(containsString("Good.java")))
			// With a report present the empty state must NOT render (th:replace/th:unless
			// precedence trap — the guard belongs on a wrapper, not the replaced div).
			.andExpect(content().string(not(containsString("No coverage yet"))));
	}

	@Test
	void coveragePageShowsEmptyStateWithoutCoverage() throws Exception {
		MockMvc mvc = mockMvc();
		long projectId = ingest(mvc, "cov-empty", false);

		mvc.perform(get("/projects/{id}/coverage", projectId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("No coverage yet")));
	}

	@Test
	void coverageUnknownProjectIsNotFound() throws Exception {
		mockMvc().perform(get("/projects/{id}/coverage", 999999)).andExpect(status().isNotFound());
	}

	/**
	 * The all-modules page shows the by-module picker, and its rows link to each module's
	 * dedicated page ({@code /coverage/module/{module}}), not the old {@code ?module=}
	 * scope.
	 */
	@Test
	void modulePickerLinksToDedicatedModulePages() throws Exception {
		MockMvc mvc = mockMvc();
		long projectId = ingest(mvc, "cov-modpick", true);

		mvc.perform(get("/projects/{id}/coverage", projectId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Coverage by module")))
			.andExpect(content().string(containsString("/projects/" + projectId + "/coverage/module/")));
	}

	/**
	 * A module's dedicated page is module-filtered (by-package still there) and drops the
	 * module picker ("Coverage by module") — you're already in the module.
	 */
	@Test
	void modulePageIsScopedAndDropsThePicker() throws Exception {
		MockMvc mvc = mockMvc();
		long projectId = ingest(mvc, "cov-modpage", true);

		// Discover a real module URL from the picker, then follow it (robust to module
		// naming).
		String all = mvc.perform(get("/projects/{id}/coverage", projectId))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Matcher m = Pattern.compile("/projects/" + projectId + "/coverage/module/[\\w.%-]+").matcher(all);
		assertThat(m.find()).as("a dedicated module link on the all-modules page").isTrue();

		mvc.perform(get(m.group()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("By package")))
			.andExpect(content().string(not(containsString("Coverage by module"))));
	}

}
