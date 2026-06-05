package org.alexmond.unitrack.web;

import com.jayway.jsonpath.JsonPath;
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
class CoverageFlagsIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private static byte[] junit(String className, String name, boolean failing) {
		String tc = failing
				? "<testcase name=\"" + name + "\" classname=\"" + className + "\" time=\"0.01\">"
						+ "<failure message=\"boom\" type=\"java.lang.AssertionError\">trace</failure></testcase>"
				: "<testcase name=\"" + name + "\" classname=\"" + className + "\" time=\"0.01\"/>";
		return ("<?xml version=\"1.0\"?><testsuite name=\"" + className + "\" tests=\"1\" failures=\""
				+ (failing ? 1 : 0) + "\" errors=\"0\" skipped=\"0\" time=\"0.01\">" + tc + "</testsuite>")
			.getBytes();
	}

	private long[] ingest(MockMvc mvc, String flag, String commit, String cls, String name, boolean failing)
			throws Exception {
		String content = mvc
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("junit", "TEST.xml", "text/xml", junit(cls, name, failing)))
				.param("project", "flags-demo")
				.param("branch", "main")
				.param("flag", flag)
				.param("commit", commit))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return new long[] { ((Number) JsonPath.read(content, "$.projectId")).longValue(),
				((Number) JsonPath.read(content, "$.runId")).longValue() };
	}

	@Test
	void gateBaselineIsScopedToFlagAndFlagsAreListed() throws Exception {
		MockMvc mvc = mockMvc();
		// ui: test X failing on an earlier commit (the ui baseline).
		long[] first = ingest(mvc, "ui", "c1", "com.x.X", "x", true);
		long projectId = first[0];
		// api: an unrelated, more recent run on a different flag.
		ingest(mvc, "api", "c2", "com.x.Y", "y", false);
		// ui again: same test X still failing -> not a NEW failure vs the ui baseline.
		long uiRunId = ingest(mvc, "ui", "c3", "com.x.X", "x", true)[1];

		// Flag-scoped baseline: X failed in the ui baseline too, so the gate passes.
		// (A flag-agnostic baseline would pick the api run and wrongly flag X as new.)
		mvc.perform(get("/api/v1/runs/{id}/quality-gate", uiRunId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.passed").value(true));

		// The run carries its flag.
		mvc.perform(get("/api/v1/runs/{id}", uiRunId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.run.flag").value("ui"));

		// Both flags are reported (ordered by flag name).
		mvc.perform(get("/api/v1/projects/{id}/flags", projectId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].flag").value("api"))
			.andExpect(jsonPath("$[1].flag").value("ui"));
	}

}
