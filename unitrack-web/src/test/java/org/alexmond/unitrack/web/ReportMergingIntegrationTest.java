package org.alexmond.unitrack.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ReportMergingIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private static byte[] junit(String name, boolean failing) {
		String tc = failing
				? "<testcase name=\"" + name + "\" classname=\"com.x.M\" time=\"0.01\">"
						+ "<failure message=\"boom\" type=\"java.lang.AssertionError\">trace</failure></testcase>"
				: "<testcase name=\"" + name + "\" classname=\"com.x.M\" time=\"0.01\"/>";
		return ("<?xml version=\"1.0\"?><testsuite name=\"com.x.M\" tests=\"1\" failures=\"" + (failing ? 1 : 0)
				+ "\" errors=\"0\" skipped=\"0\" time=\"0.01\">" + tc + "</testsuite>")
			.getBytes();
	}

	private String ingest(MockMvc mvc, String name, boolean failing, byte[] jacoco) throws Exception {
		var req = multipart("/api/v1/ingest")
			.file(new MockMultipartFile("junit", "TEST.xml", "text/xml", junit(name, failing)))
			.param("project", "merge-demo")
			.param("commit", "shardcommit")
			.param("runKey", "build-42");
		if (jacoco != null) {
			req = req.file(new MockMultipartFile("jacoco", "jacoco.xml", "text/xml", jacoco));
		}
		return mvc.perform(req).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
	}

	@Test
	void shardedUploadsWithSameRunKeyMergeIntoOneRun() throws Exception {
		MockMvc mvc = mockMvc();
		byte[] jacoco = getClass().getResourceAsStream("/samples/jacoco-sample.xml").readAllBytes();

		String first = ingest(mvc, "a", false, jacoco); // shard 1: A passes
		long runId = ((Number) JsonPath.read(first, "$.runId")).longValue();
		assertThat((int) JsonPath.read(first, "$.uploads")).isEqualTo(1);

		String second = ingest(mvc, "b", true, jacoco); // shard 2: B fails, same runKey
		long mergedRunId = ((Number) JsonPath.read(second, "$.runId")).longValue();

		// Same run, accumulated totals.
		assertThat(mergedRunId).isEqualTo(runId);
		assertThat((int) JsonPath.read(second, "$.uploads")).isEqualTo(2);
		assertThat((int) JsonPath.read(second, "$.total")).isEqualTo(2);
		assertThat(JsonPath.read(second, "$.status").toString()).isEqualTo("FAILED");

		mvc.perform(get("/api/v1/runs/{id}", runId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.run.total").value(2))
			.andExpect(jsonPath("$.run.passed").value(1))
			.andExpect(jsonPath("$.run.failed").value(1))
			.andExpect(jsonPath("$.run.uploads").value(2))
			.andExpect(jsonPath("$.suites.length()").value(2))
			.andExpect(jsonPath("$.failures.length()").value(1))
			// Coverage counters from both jacoco uploads are summed (23 + 23 lines
			// covered).
			.andExpect(jsonPath("$.coverage.lineCovered").value(46));
	}

}
