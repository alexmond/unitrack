package org.alexmond.unitrack.web;

import java.util.List;

import org.alexmond.unitrack.domain.IngestJob;
import org.alexmond.unitrack.web.ingest.IngestJobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The per-project ingest view ({@code /projects/{id}/ingest}) must show only that
 * project's jobs — no cross-project leak (an owner sees their project, not everyone's).
 */
@SpringBootTest
class ProjectIngestScopeIntegrationTest {

	private static final byte[] JUNIT = ("<?xml version=\"1.0\"?><testsuite name=\"S\" tests=\"1\" failures=\"0\" "
			+ "errors=\"0\" skipped=\"0\" time=\"0.01\"><testcase name=\"t\" classname=\"S\" time=\"0.01\"/></testsuite>")
		.getBytes();

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private IngestJobService ingestJobs;

	private void ingest(String project) throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(this.context).build();
		mvc.perform(multipart("/api/v1/ingest").file(new MockMultipartFile("junit", "TEST-S.xml", "text/xml", JUNIT))
			.param("project", project)
			.param("commit", "c1")).andExpect(status().isCreated());
	}

	@Test
	void recentForProjectReturnsOnlyThatProjectsJobs() throws Exception {
		ingest("scope-a");
		ingest("scope-b");

		List<IngestJob> a = this.ingestJobs.recentForProject("scope-a", 50);
		assertThat(a).isNotEmpty();
		assertThat(a).allMatch((j) -> "scope-a".equals(j.getProjectName()));
		assertThat(a).noneMatch((j) -> "scope-b".equals(j.getProjectName()));
	}

}
