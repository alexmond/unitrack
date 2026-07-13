package org.alexmond.unitrack.ingest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class JmhJsonParserTest {

	private final JmhJsonParser parser = new JmhJsonParser();

	private static final String REPORT = """
			[
			  {
			    "jmhVersion" : "1.37",
			    "benchmark" : "org.example.MyBench.render",
			    "mode" : "avgt",
			    "forks" : 5,
			    "measurementIterations" : 5,
			    "params" : { "size" : "100" },
			    "primaryMetric" : {
			      "score" : 120.0,
			      "scoreUnit" : "us/op",
			      "scorePercentiles" : {
			        "0.0" : 100.0, "50.0" : 118.0, "90.0" : 130.0, "95.0" : 135.0, "99.0" : 140.0, "100.0" : 150.0
			      }
			    }
			  },
			  {
			    "jmhVersion" : "1.37",
			    "benchmark" : "org.example.MyBench.throughput",
			    "mode" : "thrpt",
			    "forks" : 2,
			    "measurementIterations" : 5,
			    "primaryMetric" : {
			      "score" : 2000.0,
			      "scoreUnit" : "ops/s",
			      "scorePercentiles" : { "50.0" : 1.0 }
			    }
			  }
			]
			""";

	@Test
	void detectsJmhReportNotOtherFormats() {
		assertThat(parser.supports(REPORT)).isTrue();
		assertThat(parser.supports("{ \"metrics\": { \"http_req_duration\": {} } }")).isFalse(); // k6
		assertThat(parser.supports("timeStamp,elapsed,label,success\n")).isFalse(); // JMeter
																					// JTL
		assertThat(parser.supports("[ { \"foo\": 1 } ]")).isFalse(); // plain array, no
																		// jmhVersion
	}

	@Test
	void parsesAvgtBenchmarkConvertingUnitAndPercentiles() {
		PerfResults r = parser.parse(new ByteArrayInputStream(REPORT.getBytes(StandardCharsets.UTF_8)));
		PerfResults.LabelStats render = r.labels()
			.stream()
			.filter((l) -> l.label().startsWith("MyBench.render"))
			.findFirst()
			.orElseThrow();
		// label is Class.method[params]
		assertThat(render.label()).isEqualTo("MyBench.render[size=100]");
		// us/op -> ms (x1e-3)
		assertThat(render.meanMs()).isCloseTo(0.120, within(1e-6));
		assertThat(render.p50Ms()).isCloseTo(0.118, within(1e-6));
		assertThat(render.p99Ms()).isCloseTo(0.140, within(1e-6));
		// forks(5) x measurementIterations(5)
		assertThat(render.sampleCount()).isEqualTo(25);
		// JMH has no per-benchmark error concept
		assertThat(render.errorCount()).isZero();
		assertThat(render.errorPct()).isZero();
	}

	@Test
	void parsesThroughputBenchmarkInvertingToMeanMsWithFlatPercentiles() {
		PerfResults r = parser.parse(new ByteArrayInputStream(REPORT.getBytes(StandardCharsets.UTF_8)));
		PerfResults.LabelStats tput = r.labels()
			.stream()
			.filter((l) -> l.label().startsWith("MyBench.throughput"))
			.findFirst()
			.orElseThrow();
		// 2000 ops/s -> 1000/2000 = 0.5 ms/op
		assertThat(tput.meanMs()).isCloseTo(0.5, within(1e-6));
		// throughput percentiles do not invert to latency tails -> reported flat at the
		// mean
		assertThat(tput.p50Ms()).isCloseTo(0.5, within(1e-6));
		assertThat(tput.p99Ms()).isCloseTo(0.5, within(1e-6));
		assertThat(tput.sampleCount()).isEqualTo(10); // forks(2) x iterations(5)
	}

	@Test
	void rollsUpMeanWeightedButPercentilesAsWorstAcrossBenchmarks() {
		PerfResults r = parser.parse(new ByteArrayInputStream(REPORT.getBytes(StandardCharsets.UTF_8)));
		assertThat(r.labels()).hasSize(2);
		assertThat(r.sampleCount()).isEqualTo(35); // 25 + 10
		assertThat(r.errorCount()).isZero();
		// Mean is sample-weighted (linear → exact pooled mean): (0.120*25 + 0.5*10) / 35.
		assertThat(r.meanMs()).isCloseTo((0.120 * 25 + 0.5 * 10) / 35.0, within(1e-6));
		assertThat(r.minMs()).isCloseTo(0.120, within(1e-6));
		assertThat(r.maxMs()).isCloseTo(0.5, within(1e-6));
		// Percentiles are the WORST across benchmarks, not the sample-weighted average
		// (which would have hidden the tput benchmark's flat 0.5 behind render's smaller
		// 0.118-0.140 tail). tput is 0.5 at every percentile → run-level max = 0.5.
		assertThat(r.p50Ms()).isCloseTo(0.5, within(1e-6));
		assertThat(r.p90Ms()).isCloseTo(0.5, within(1e-6));
		assertThat(r.p95Ms()).isCloseTo(0.5, within(1e-6));
		assertThat(r.p99Ms()).isCloseTo(0.5, within(1e-6));
	}

	@Test
	void convertsNanosecondScores() {
		String ns = """
				[ {
				  "jmhVersion" : "1.37",
				  "benchmark" : "org.example.Hot.tiny",
				  "mode" : "avgt",
				  "forks" : 1,
				  "measurementIterations" : 1,
				  "primaryMetric" : { "score" : 5000.0, "scoreUnit" : "ns/op", "scorePercentiles" : {} }
				} ]
				""";
		PerfResults r = parser.parse(new ByteArrayInputStream(ns.getBytes(StandardCharsets.UTF_8)));
		PerfResults.LabelStats l = r.labels().get(0);
		assertThat(l.label()).isEqualTo("Hot.tiny");
		// 5000 ns -> 0.005 ms; missing percentiles fall back to the score
		assertThat(l.meanMs()).isCloseTo(0.005, within(1e-9));
		assertThat(l.p50Ms()).isCloseTo(0.005, within(1e-9));
	}

	@Test
	void rejectsEmptyOrNonArray() {
		assertThat(catchIngest("[]")).isTrue();
		assertThat(catchIngest("{ \"jmhVersion\": \"1.37\" }")).isTrue();
	}

	private boolean catchIngest(String json) {
		try {
			parser.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
			return false;
		}
		catch (IngestException ex) {
			return true;
		}
	}

}
