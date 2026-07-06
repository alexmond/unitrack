package org.alexmond.unitrack.ingest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class JmeterJtlParserTest {

	private final JmeterJtlParser parser = new JmeterJtlParser();

	private PerfResults parse(String jtl) {
		return this.parser.parse(new ByteArrayInputStream(jtl.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void toleratesATruncatedTailFromAnAbortedRun() {
		// An aborted/killed load test leaves a half-written final row. Streaming
		// line-parsing
		// keeps every complete sample and just drops the partial tail (no all-or-nothing
		// fail).
		String jtl = "timeStamp,elapsed,label,responseCode,success\n" + "1000,100,GET /a,200,true\n"
				+ "1100,200,GET /a,200,true\n" + "1200,30"; // <- truncated mid-row
		PerfResults r = parse(jtl);
		assertThat(r.sampleCount()).isEqualTo(2);
		assertThat(r.maxMs()).isEqualTo(200);
	}

	@Test
	void detectsJtlAndIgnoresOtherFormats() {
		assertThat(parser.supports("timeStamp,elapsed,label,success\n")).isTrue();
		assertThat(parser.supports("{\"metrics\":{}}")).isFalse(); // k6 JSON
		assertThat(parser.supports("<report></report>")).isFalse(); // jacoco XML
		assertThat(parser.supports("just,some,csv\n")).isFalse();
	}

	@Test
	void computesAggregateAndPerLabelPercentiles() {
		String jtl = """
				timeStamp,elapsed,label,responseCode,success
				1000,100,GET /a,200,true
				1100,200,GET /a,200,true
				1200,300,GET /a,500,false
				1300,50,GET /b,200,true
				""";
		PerfResults r = parse(jtl);

		assertThat(r.sampleCount()).isEqualTo(4);
		assertThat(r.errorCount()).isEqualTo(1);
		assertThat(r.errorPct()).isCloseTo(25.0, within(0.01));
		assertThat(r.minMs()).isEqualTo(50);
		assertThat(r.maxMs()).isEqualTo(300);
		assertThat(r.meanMs()).isCloseTo(162.5, within(0.01));
		// sorted [50,100,200,300]: p50=100, p90/p95/p99=300 (nearest-rank)
		assertThat(r.p50Ms()).isEqualTo(100);
		assertThat(r.p90Ms()).isEqualTo(300);
		assertThat(r.p99Ms()).isEqualTo(300);
		// duration = maxEnd(1500) - minStart(1000) = 500ms; 4 samples / 0.5s = 8 rps
		assertThat(r.durationMs()).isEqualTo(500);
		assertThat(r.throughputRps()).isCloseTo(8.0, within(0.01));

		assertThat(r.labels()).hasSize(2);
		PerfResults.LabelStats a = r.labels()
			.stream()
			.filter((l) -> l.label().equals("GET /a"))
			.findFirst()
			.orElseThrow();
		assertThat(a.sampleCount()).isEqualTo(3);
		assertThat(a.errorCount()).isEqualTo(1);
		assertThat(a.p50Ms()).isEqualTo(200); // [100,200,300] -> rank ceil(1.5)=2 -> 200
		assertThat(a.meanMs()).isCloseTo(200.0, within(0.01));
	}

	@Test
	void handlesBomUnorderedColumnsAndQuotedLabels() {
		// BOM prefix, columns in a different order, a label containing a comma (quoted).
		String jtl = "﻿label,success,elapsed,timeStamp\n" + "\"GET /a,b\",true,120,1000\n";
		PerfResults r = parse(jtl);
		assertThat(r.sampleCount()).isEqualTo(1);
		assertThat(r.labels()).hasSize(1);
		assertThat(r.labels().get(0).label()).isEqualTo("GET /a,b");
		assertThat(r.p95Ms()).isEqualTo(120);
	}

	@Test
	void emptyFileYieldsZeroes() {
		PerfResults r = parse("timeStamp,elapsed,label,success\n");
		assertThat(r.sampleCount()).isZero();
		assertThat(r.p95Ms()).isZero();
		assertThat(r.throughputRps()).isZero();
		assertThat(r.labels()).isEmpty();
	}

	@Test
	void rejectsFileMissingRequiredColumns() {
		assertThatThrownBy(() -> parse("timeStamp,foo,bar\n1,2,3\n")).isInstanceOf(IngestException.class);
	}

}
