package org.alexmond.unitrack.report;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;

import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.ingest.IngestRequest;
import org.alexmond.unitrack.ingest.IngestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests-by-module groups on the explicit module the uploader sends (#393) rather than the
 * package-derivation heuristic. Two per-module uploads of one build merge into a single
 * run via {@code run_key}, each tagging its results with its module.
 */
@SpringBootTest
@Transactional
class TestModuleGroupingTest {

	@Autowired
	private IngestService ingest;

	@Autowired
	private ReportingService reporting;

	private List<Supplier<InputStream>> junit() throws Exception {
		byte[] xml = getClass().getResourceAsStream("/samples/surefire-sample.xml").readAllBytes();
		return List.of(() -> new ByteArrayInputStream(xml));
	}

	@Test
	void groupsByExplicitUploaderModule() throws Exception {
		TestRun run = this.ingest.ingest(new IngestRequest("modgroup", null, "main", "default", "c1", null, null, null,
				"rk-mod", null, null, "alpha"), junit(), List.of());
		// Same build (same run_key) uploaded again for a second module — merges into one
		// run.
		this.ingest.ingest(new IngestRequest("modgroup", null, "main", "default", "c1", null, null, null, "rk-mod",
				null, null, "beta"), junit(), List.of());

		List<TestModuleRow> modules = this.reporting.testModules(run.getId());
		assertThat(modules).extracting(TestModuleRow::name).containsExactly("alpha", "beta");
		assertThat(modules).allMatch((m) -> m.tests() > 0);
	}

}
