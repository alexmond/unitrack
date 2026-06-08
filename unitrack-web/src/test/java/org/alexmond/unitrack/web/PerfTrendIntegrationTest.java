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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PerfTrendIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private static final byte[] JTL = ("timeStamp,elapsed,label,success\n" + "1000,100,GET /a,true\n"
			+ "1100,200,GET /a,true\n" + "1200,300,GET /a,false\n")
		.getBytes();

	private long ingest(MockMvc mvc, String project, String commit) throws Exception {
		String body = mvc
			.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("perf", "results.jtl", "text/csv", JTL))
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
	void perfTrendEndpointAndPageReturnData() throws Exception {
		MockMvc mvc = mockMvc();
		long projectId = ingest(mvc, "perf-trend", "c1");
		ingest(mvc, "perf-trend", "c2");

		// REST trend (oldest first).
		mvc.perform(get("/api/v1/projects/{id}/perf-trend", projectId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].p95Ms").value(300.0))
			.andExpect(jsonPath("$[0].errorPct").exists());

		// Dashboard page renders with the chart canvases.
		mvc.perform(get("/projects/{id}/perf", projectId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("load tests")))
			.andExpect(content().string(containsString("latencyChart")));
	}

	@Test
	void perfTrendUnknownProjectIsNotFound() throws Exception {
		mockMvc().perform(get("/api/v1/projects/{id}/perf-trend", 999999)).andExpect(status().isNotFound());
	}

}
