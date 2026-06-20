package org.alexmond.unitrack.web.ui;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.ExtendedModelMap;

import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.ingest.IngestRequest;
import org.alexmond.unitrack.ingest.IngestService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Read-path instrumentation check for the run page (#280). Ingests a run, renders the
 * page via {@link DashboardController}, and prints the per-section timing the
 * {@code unitrack.page} observations record — so the dominant contributors are visible
 * before optimizing — with a generous wall-clock budget as a regression guard to tighten
 * as the read path is sped up.
 */
@SpringBootTest
@Transactional
class RunPagePerfTest {

	@Autowired
	private IngestService ingest;

	@Autowired
	private DashboardController controller;

	@Autowired
	private MeterRegistry meters;

	@Test
	void runPageSectionBreakdownIsUnderBudget() throws Exception {
		byte[] junit = getClass().getResourceAsStream("/samples/surefire-sample.xml").readAllBytes();
		byte[] jacoco = getClass().getResourceAsStream("/samples/jacoco-sample.xml").readAllBytes();
		List<Supplier<InputStream>> junitStreams = List.of(() -> new ByteArrayInputStream(junit));
		List<Supplier<InputStream>> jacocoStreams = List.of(() -> new ByteArrayInputStream(jacoco));
		IngestRequest meta = new IngestRequest("perf-demo", null, "main", null, "cafe1234", null, null, null);
		TestRun run = this.ingest.ingest(meta, junitStreams, jacocoStreams);

		long start = System.nanoTime();
		this.controller.run(run.getId(), new ExtendedModelMap());
		long totalMs = (System.nanoTime() - start) / 1_000_000;

		System.out.println("=== run page section breakdown (ms) ===");
		this.meters.find("unitrack.page")
			.timers()
			.stream()
			.sorted(Comparator.comparingDouble((Timer t) -> t.totalTime(TimeUnit.MILLISECONDS)).reversed())
			.forEach((t) -> System.out.printf("  %-16s %6.1f%n", t.getId().getTag("section"),
					t.totalTime(TimeUnit.MILLISECONDS)));
		System.out.println("  total handler:   " + totalMs);

		// Generous guard; tighten as the read path is optimized (#280 targets run page <
		// 400ms).
		assertThat(totalMs).isLessThan(2000L);
	}

}
