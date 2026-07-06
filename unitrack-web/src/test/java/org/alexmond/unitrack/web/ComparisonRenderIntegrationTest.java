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
 * The Compare view ({@code /compare?base=&head=}) — reached by clicking a trend-chart
 * point. This renders {@code compare.html}, so it guards against template-expression
 * breakage there (a malformed {@code th:text} once 500'd every compare with no test to
 * catch it).
 */
@SpringBootTest
class ComparisonRenderIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private static byte[] junit(boolean pass) {
		String caseBody = pass ? "<testcase name=\"t\" classname=\"com.x.X\" time=\"0.1\"/>"
				: "<testcase name=\"t\" classname=\"com.x.X\" time=\"0.1\"><failure message=\"boom\">x</failure></testcase>";
		return ("<?xml version=\"1.0\"?><testsuite name=\"s\" tests=\"1\" failures=\"" + (pass ? "0" : "1")
				+ "\" errors=\"0\" skipped=\"0\" time=\"0.1\">" + caseBody + "</testsuite>")
			.getBytes();
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
		return ((Number) JsonPath.read(body, "$.runId")).longValue();
	}

	@Test
	void compareTwoRunsRendersOk() throws Exception {
		MockMvc mvc = mockMvc();
		long base = ingest(mvc, "compare-render", "aaa1111", true);
		long head = ingest(mvc, "compare-render", "bbb2222", false);

		mvc.perform(get("/compare").param("base", String.valueOf(base)).param("head", String.valueOf(head)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Compare runs")))
			// The base/head commit labels render (the th:text that used to fail to
			// parse).
			.andExpect(content().string(containsString("aaa1111")))
			.andExpect(content().string(containsString("bbb2222")));
	}

}
