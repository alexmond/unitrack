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

@SpringBootTest
class CoverageDiffIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private static byte[] junit() {
		return ("<?xml version=\"1.0\"?><testsuite name=\"s\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\" "
				+ "time=\"0.1\"><testcase name=\"t\" classname=\"com.x.X\" time=\"0.1\"/></testsuite>")
			.getBytes();
	}

	private static String file(String pkg, String name, int covered, int missed) {
		return "<package name=\"" + pkg + "\"><sourcefile name=\"" + name + "\">" + "<counter type=\"LINE\" missed=\""
				+ missed + "\" covered=\"" + covered + "\"/></sourcefile></package>";
	}

	private static byte[] jacoco(String files, int totalCovered, int totalMissed) {
		return ("<?xml version=\"1.0\"?><report name=\"r\">" + files + "<counter type=\"LINE\" missed=\"" + totalMissed
				+ "\" covered=\"" + totalCovered + "\"/></report>")
			.getBytes();
	}

	private long ingest(MockMvc mvc, String project, String commit, byte[] jacoco) throws Exception {
		String body = mvc
			.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST.xml", "text/xml", junit()))
				.file(new MockMultipartFile("jacoco", "coverage.xml", "text/xml", jacoco))
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
	void runShowsPerFileCoverageDiffVersusBaseline() throws Exception {
		MockMvc mvc = mockMvc();
		// Baseline: Good 90%, Bad 40%, Gone 50% (total 60%).
		ingest(mvc, "cov-diff", "base01", jacoco(file("com/acme/svc", "Good.java", 90, 10)
				+ file("com/acme/web", "Bad.java", 40, 60) + file("com/acme/util", "Gone.java", 50, 50), 180, 120));
		// Current: Good drops to 80%, Bad improves to 60%, Gone removed, New added 70%
		// (total 70%).
		long current = ingest(mvc, "cov-diff", "curr02", jacoco(file("com/acme/svc", "Good.java", 80, 20)
				+ file("com/acme/web", "Bad.java", 60, 40) + file("com/acme/svc", "New.java", 70, 30), 210, 90));

		mvc.perform(get("/runs/{id}", current))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Coverage change")))
			.andExpect(content().string(containsString("pp overall")))
			.andExpect(content().string(containsString("dropped")))
			.andExpect(content().string(containsString("improved")))
			.andExpect(content().string(containsString("added")))
			.andExpect(content().string(containsString("removed")))
			.andExpect(content().string(containsString("com/acme/svc/New.java")))
			.andExpect(content().string(containsString("com/acme/util/Gone.java")));
	}

	@Test
	void firstRunHasNoBaselineSoNoDiffSection() throws Exception {
		MockMvc mvc = mockMvc();
		long only = ingest(mvc, "cov-diff-single", "solo01", jacoco(file("com/acme", "A.java", 80, 20), 80, 20));

		mvc.perform(get("/runs/{id}", only))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.not(containsString("Coverage change"))));
	}

}
