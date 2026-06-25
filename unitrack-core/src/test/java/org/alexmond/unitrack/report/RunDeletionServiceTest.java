package org.alexmond.unitrack.report;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;

import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.ingest.IngestRequest;
import org.alexmond.unitrack.ingest.IngestService;
import org.alexmond.unitrack.repository.CoverageReportRepository;
import org.alexmond.unitrack.repository.TestCaseResultRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deleting a run must take its children with it (cases, suites, coverage report + files,
 * share links) — the FKs have no ON DELETE CASCADE, so the service deletes them
 * explicitly. Used to purge bad uploads.
 */
@SpringBootTest
class RunDeletionServiceTest {

	@Autowired
	private IngestService ingest;

	@Autowired
	private RunDeletionService deletion;

	@Autowired
	private TestRunRepository runs;

	@Autowired
	private TestCaseResultRepository cases;

	@Autowired
	private CoverageReportRepository coverageReports;

	private List<Supplier<InputStream>> resource(String path) throws Exception {
		byte[] bytes = getClass().getResourceAsStream(path).readAllBytes();
		return List.of(() -> new ByteArrayInputStream(bytes));
	}

	@Test
	void deleteRemovesRunAndChildren() throws Exception {
		TestRun run = this.ingest.ingest(new IngestRequest("rundel", null, "main", "default", "c1", null, null, null),
				resource("/samples/surefire-sample.xml"), resource("/samples/jacoco-sample.xml"));
		Long id = run.getId();
		assertThat(this.cases.findByRunIdOrderByStatusAscClassNameAscNameAsc(id)).isNotEmpty();
		assertThat(this.coverageReports.findByRunId(id)).isPresent();

		this.deletion.deleteRun(id);

		assertThat(this.runs.findById(id)).isEmpty();
		assertThat(this.cases.findByRunIdOrderByStatusAscClassNameAscNameAsc(id)).isEmpty();
		assertThat(this.coverageReports.findByRunId(id)).isEmpty();
	}

}
