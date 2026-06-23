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
 * A trend must chart a single flag so split-by-module's per-module flags don't interleave
 * with the default rollup into a sawtooth (#280/#225 split). The flag-scoped trend
 * returns only that flag's runs; the unscoped one still sees every flag.
 */
@SpringBootTest
@Transactional
class TrendFlagScopeTest {

	@Autowired
	private IngestService ingest;

	@Autowired
	private ReportingService reporting;

	@Autowired
	private PerformanceService performance;

	private List<Supplier<InputStream>> junit() throws Exception {
		byte[] xml = getClass().getResourceAsStream("/samples/surefire-sample.xml").readAllBytes();
		return List.of(() -> new ByteArrayInputStream(xml));
	}

	@Test
	void trendScopedToFlagExcludesOtherFlags() throws Exception {
		Long pid = this.ingest
			.ingest(new IngestRequest("trendflag", null, "main", "default", "c1", null, null, null), junit(), List.of())
			.getProject()
			.getId();
		// A per-module run on the same project, different flag — must not pollute the
		// default trend.
		this.ingest.ingest(new IngestRequest("trendflag", null, "main", "modA", "c1", null, null, null), junit(),
				List.of());

		List<TestRun> defaultTrend = this.reporting.trendRuns(pid, null, "default", 30);
		assertThat(defaultTrend).hasSize(1).allMatch((r) -> "default".equals(r.getFlag()));
		// Unscoped still sees both flags' runs.
		assertThat(this.reporting.trendRuns(pid, null, 30)).hasSize(2);

		// The suite-time (performance) trend has the same flag-scope contract — without
		// it, a
		// split-by-module project's per-module suite times sawtooth (#280).
		assertThat(this.performance.suiteTimeTrend(pid, "default", 30)).hasSize(1);
		assertThat(this.performance.suiteTimeTrend(pid, 30)).hasSize(2);
		assertThat(this.performance.summary(pid, "default", 25, 30).suiteTimeTrend()).hasSize(1);

		// The per-test timeline too: the same test ran in both flags' runs, so the
		// unscoped
		// history double-plots each commit; scoping to the rollup flag gives one point.
		assertThat(this.performance.testStatusTimeline(pid, "com.example.CalculatorTest", "adds", "default", 30))
			.hasSize(1);
		assertThat(this.performance.testStatusTimeline(pid, "com.example.CalculatorTest", "adds", 30)).hasSize(2);
	}

}
