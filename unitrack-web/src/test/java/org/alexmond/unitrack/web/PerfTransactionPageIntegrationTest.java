package org.alexmond.unitrack.web;

import org.alexmond.unitrack.repository.PerfRunRepository;
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
 * The per-transaction detail page — the drill-in from the Load tests "By transaction"
 * table. Verifies a label with ≥2 runs renders its latency-over-runs trend + per-run
 * history, and an unknown label degrades to an empty state (not a 500).
 */
@SpringBootTest
class PerfTransactionPageIntegrationTest {

	private static byte[] jtl(String label) {
		return ("timeStamp,elapsed,label,success\n1000,100," + label + ",true\n1100,120," + label + ",true\n")
			.getBytes();
	}

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private PerfRunRepository perfRuns;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(this.context).build();
	}

	private void ingest(MockMvc mvc, String project, String commit) throws Exception {
		mvc.perform(multipart("/api/v1/ingest")
			.file(new MockMultipartFile("perf", "results.jtl", "text/csv", jtl("GET /api")))
			.param("project", project)
			.param("commit", commit)
			.param("perfFlag", "default")).andExpect(status().isCreated());
	}

	private Long projectIdFor(String project) {
		return this.perfRuns.findAll()
			.stream()
			.filter((r) -> project.equals(r.getProject().getName()))
			.map((r) -> r.getProject().getId())
			.findFirst()
			.orElseThrow();
	}

	@Test
	void transactionDetailRendersTrendAndHistory() throws Exception {
		MockMvc mvc = mockMvc();
		ingest(mvc, "txn-detail", "aaa1");
		ingest(mvc, "txn-detail", "bbb2");
		long id = projectIdFor("txn-detail");

		mvc.perform(get("/projects/{id}/perf/transaction", id).param("label", "GET /api"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("GET /api")))
			.andExpect(content().string(containsString("Latency over runs")))
			.andExpect(content().string(containsString("Per-run history")));
	}

	@Test
	void unknownTransactionShowsEmptyStateNotError() throws Exception {
		MockMvc mvc = mockMvc();
		ingest(mvc, "txn-empty", "ccc1");
		long id = projectIdFor("txn-empty");

		mvc.perform(get("/projects/{id}/perf/transaction", id).param("label", "POST /nope"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("No data for this transaction")));
	}

}
