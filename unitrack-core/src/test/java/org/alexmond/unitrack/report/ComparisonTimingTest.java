package org.alexmond.unitrack.report;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

@SpringBootTest
@Transactional
class ComparisonTimingTest {

	@Autowired
	private IngestService ingest;

	@Autowired
	private ComparisonService comparison;

	/** A suite with test {@code C#a} (aSec) and optionally {@code C#b} (bSec). */
	private static byte[] junit(double aSec, double bSec, boolean includeB) {
		String xml = "<?xml version=\"1.0\"?><testsuite name=\"S\" tests=\"" + (includeB ? 2 : 1)
				+ "\" failures=\"0\" errors=\"0\" skipped=\"0\">" + "<testcase name=\"a\" classname=\"C\" time=\""
				+ aSec + "\"/>" + (includeB ? "<testcase name=\"b\" classname=\"C\" time=\"" + bSec + "\"/>" : "")
				+ "</testsuite>";
		return xml.getBytes(StandardCharsets.UTF_8);
	}

	private TestRun ingestRun(String commit, byte[] junit) {
		IngestRequest meta = new IngestRequest("cmp-timing", null, "main", null, commit, null, null, null, null, null,
				null);
		List<Supplier<InputStream>> streams = List.of(() -> new ByteArrayInputStream(junit));
		return this.ingest.ingest(meta, streams, List.of());
	}

	@Test
	void timingDeltasShowPerTestChangesNewestVsBase() {
		TestRun base = ingestRun("c1", junit(0.1, 0.1, true)); // a=100ms, b=100ms
		TestRun head = ingestRun("c2", junit(0.5, 0.1, false)); // a=500ms, b removed

		RunComparison cmp = this.comparison.compare(base.getId(), head.getId()).orElseThrow();
		List<TestTimingDelta> deltas = cmp.timingDeltas();

		// a slowed 100 -> 500 (+400, SLOWER), biggest change so it sorts first.
		assertThat(deltas.getFirst().test()).isEqualTo("C#a");
		assertThat(deltas.getFirst().kind()).isEqualTo(TestTimingDelta.Kind.SLOWER);
		assertThat(deltas.getFirst().deltaMs()).isEqualTo(400);
		// b was present in base only -> REMOVED; unchanged tests produce no row.
		assertThat(deltas).anyMatch((d) -> d.test().equals("C#b") && d.kind() == TestTimingDelta.Kind.REMOVED);
	}

}
