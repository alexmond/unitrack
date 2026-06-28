package org.alexmond.unitrack.report;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.alexmond.unitrack.domain.PerfRun;
import org.alexmond.unitrack.ingest.IngestRequest;
import org.alexmond.unitrack.ingest.PerfIngestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PerfStepDetectionServiceTest {

	@Autowired
	private PerfIngestService perfIngest;

	@Autowired
	private PerfStepDetectionService detection;

	/** A single-series perf upload whose p95 ≈ {@code latencyMs}. */
	private PerfRun ingest(String project, String commit, int latencyMs) {
		byte[] jtl = ("timeStamp,elapsed,label,success\n1000," + latencyMs + ",GET /,true\n1100," + latencyMs
				+ ",GET /,true\n")
			.getBytes(StandardCharsets.UTF_8);
		IngestRequest meta = new IngestRequest(project, null, "main", null, commit, null, null, null, null, null, null);
		List<Supplier<InputStream>> stream = List.of(() -> new ByteArrayInputStream(jtl));
		return this.perfIngest.ingest(meta, stream);
	}

	@Test
	void detectsASustainedP95StepAndItsOnset() {
		for (int i = 0; i < 7; i++) {
			ingest("perf-step", "base" + i, 100);
		}
		for (int i = 0; i < 4; i++) {
			ingest("perf-step", "reg" + i, 220);
		}
		Long projectId = ingest("perf-step", "reg-last", 220).getProject().getId();

		Optional<PerfStepSignal> signal = this.detection.detectLatencyStep(projectId, "default");
		assertThat(signal).isPresent();
		assertThat(signal.get().depthZ()).isGreaterThan(3.0);
		assertThat(signal.get().recentMedian()).isGreaterThan(signal.get().baselineMedian());
		assertThat(signal.get().onsetCommit()).startsWith("reg");
	}

	@Test
	void aStableSeriesProducesNoSignal() {
		Long projectId = null;
		for (int i = 0; i < 11; i++) {
			projectId = ingest("perf-flat", "c" + i, 100).getProject().getId();
		}
		assertThat(this.detection.detectLatencyStep(projectId, "default")).isEmpty();
	}

}
