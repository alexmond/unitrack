package org.alexmond.unitrack.web;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.Visibility;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Embeddable badges: render for public projects, 404 for private (no probing). */
@SpringBootTest
class BadgeIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private ProjectRepository projects;

	@Autowired
	private TestRunRepository runs;

	private Long publicId;

	private Long privateId;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@BeforeEach
	void seed() {
		Project pub = projects.findByName("badge-public")
			.orElseGet(() -> projects.save(new Project("badge-public", null)));
		pub.setVisibility(Visibility.PUBLIC);
		publicId = projects.save(pub).getId();
		if (runs.findByProjectIdOrderByCreatedAtDesc(publicId, org.springframework.data.domain.PageRequest.ofSize(1))
			.isEmpty()) {
			TestRun run = new TestRun(pub, "main", "default", "sha-badge", null, null);
			run.applyTotals(9, 1, 0, 0, 1000);
			run.setLineCoveragePct(85.0);
			runs.save(run);
		}

		Project prv = projects.findByName("badge-private")
			.orElseGet(() -> projects.save(new Project("badge-private", null)));
		prv.setVisibility(Visibility.PRIVATE);
		privateId = projects.save(prv).getId();
	}

	@Test
	void publicCoverageAndPassBadgesRender() throws Exception {
		MockMvc mvc = mvc();
		mvc.perform(get("/badge/{id}/coverage.svg", publicId))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
			.andExpect(content().string(containsString("coverage")))
			.andExpect(content().string(containsString("85%")));
		mvc.perform(get("/badge/{id}/pass.svg", publicId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("90% pass")));
	}

	@Test
	void shieldsEndpointReturnsJson() throws Exception {
		mvc().perform(get("/badge/{id}/coverage", publicId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.schemaVersion").value(1))
			.andExpect(jsonPath("$.label").value("coverage"))
			.andExpect(jsonPath("$.message").value("85%"));
	}

	@Test
	void privateProjectBadgeIsHidden() throws Exception {
		mvc().perform(get("/badge/{id}/coverage.svg", privateId)).andExpect(status().isNotFound());
	}

	@Test
	void unknownMetricIs404() throws Exception {
		mvc().perform(get("/badge/{id}/bogus.svg", publicId)).andExpect(status().isNotFound());
	}

}
