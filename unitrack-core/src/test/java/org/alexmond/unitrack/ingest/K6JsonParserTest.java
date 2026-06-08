package org.alexmond.unitrack.ingest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class K6JsonParserTest {

	private final K6JsonParser parser = new K6JsonParser();

	private static final String SUMMARY = """
			{
			  "metrics": {
			    "http_req_duration": { "type": "trend", "values": {
			      "avg": 12.5, "min": 1.0, "med": 10.0, "max": 99.0, "p(90)": 30.0, "p(95)": 50.0, "p(99)": 90.0 } },
			    "http_reqs": { "type": "counter", "values": { "count": 1000, "rate": 50.0 } },
			    "http_req_failed": { "type": "rate", "values": { "rate": 0.02, "passes": 980, "fails": 20 } }
			  }
			}
			""";

	@Test
	void detectsK6SummaryNotOtherFormats() {
		assertThat(parser.supports(SUMMARY)).isTrue();
		assertThat(parser.supports("timeStamp,elapsed,label,success\n")).isFalse(); // JMeter
																					// JTL
		assertThat(parser.supports("<report></report>")).isFalse();
	}

	@Test
	void parsesK6SummaryMetrics() {
		PerfResults r = parser.parse(new ByteArrayInputStream(SUMMARY.getBytes(StandardCharsets.UTF_8)));
		assertThat(r.sampleCount()).isEqualTo(1000);
		assertThat(r.errorCount()).isEqualTo(20);
		assertThat(r.errorPct()).isCloseTo(2.0, within(0.001));
		assertThat(r.throughputRps()).isCloseTo(50.0, within(0.001));
		assertThat(r.p50Ms()).isCloseTo(10.0, within(0.001));
		assertThat(r.p95Ms()).isCloseTo(50.0, within(0.001));
		assertThat(r.p99Ms()).isCloseTo(90.0, within(0.001));
		assertThat(r.meanMs()).isCloseTo(12.5, within(0.001));
		assertThat(r.minMs()).isCloseTo(1.0, within(0.001));
		assertThat(r.maxMs()).isCloseTo(99.0, within(0.001));
		// 1000 reqs / 50 rps -> 20s
		assertThat(r.durationMs()).isEqualTo(20000);
		assertThat(r.labels()).isEmpty();
	}

}
