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
class BlameIntegrationTest {

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

	private long ingest(MockMvc mvc, String commit, boolean failing) throws Exception {
		String content = mvc
			.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("junit", "TEST-G.xml", "text/xml", junit(failing)))
				.param("project", "blame-demo")
				.param("branch", "main")
				.param("commit", commit))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(content, "$.runId")).longValue();
	}

	@Test
	void blamePointsAtFirstFailureSinceLastGreen() throws Exception {
		MockMvc mvc = mockMvc();
		ingest(mvc, "c1-green", false); // passed
		long firstFail = ingest(mvc, "c2-broke", true); // failing streak starts here
		long latest = ingest(mvc, "c3-stillred", true); // still failing

		// On the latest red run, the blame for 'a' points at the run that first broke it.
		mvc.perform(get("/api/v1/runs/{id}/blame", latest))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].name").value("a"))
			.andExpect(jsonPath("$[0].firstFailingRunId").value((int) firstFail))
			.andExpect(jsonPath("$[0].firstFailingCommit").value("c2-broke"));
	}

	@Test
	void noBlameWhenNothingFails() throws Exception {
		MockMvc mvc = mockMvc();
		long green = ingest(mvc, "all-green", false);
		mvc.perform(get("/api/v1/runs/{id}/blame", green))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void unknownRunIsNotFound() throws Exception {
		mockMvc().perform(get("/api/v1/runs/{id}/blame", 999999)).andExpect(status().isNotFound());
	}

}
