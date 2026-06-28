package org.alexmond.unitrack.ingest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses a JMH microbenchmark result produced with {@code -rf json}. The report is a JSON
 * array with one element per benchmark; each element maps onto one
 * {@link PerfResults.LabelStats} (the tracked unit), labelled
 * {@code Class.method[params]}.
 *
 * <p>
 * The common model is latency-in-ms, lower-is-better, so scores are normalised:
 * time-per-op modes ({@code avgt}/{@code sample}/{@code ss}) convert the
 * {@code scoreUnit} to ms and read {@code scorePercentiles} directly; throughput mode
 * ({@code thrpt}) inverts ops/sec to a mean ms/op and reports flat percentiles (a
 * throughput distribution does not invert to latency tails — prefer {@code avgt} for true
 * percentiles). JMH has no per-benchmark error concept, so error counts are zero.
 */
@Component
public class JmhJsonParser implements PerfResultParser {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	@Override
	public String format() {
		return "jmh";
	}

	@Override
	public boolean supports(String headSample) {
		String h = headSample.stripLeading();
		// jmhVersion is the first key of every benchmark element, so it is always within
		// the
		// head sample (unlike primaryMetric, which can fall past the sampled prefix).
		return h.startsWith("[") && headSample.contains("jmhVersion");
	}

	@Override
	public PerfResults parse(InputStream in) {
		// Stream the top-level array one benchmark element at a time so a report with
		// many
		// forks/benchmarks (rawData can be large) never loads the whole document (#369).
		try (JsonParser p = MAPPER.createParser(in)) {
			if (p.nextToken() != JsonToken.START_ARRAY) {
				throw new IngestException("Not a JMH JSON report: expected a non-empty array of benchmarks");
			}
			List<PerfResults.LabelStats> labels = new ArrayList<>();
			boolean any = false;
			while (p.nextToken() != JsonToken.END_ARRAY) {
				any = true;
				JsonNode bench = p.readValueAsTree();
				PerfResults.LabelStats stats = toLabelStats(bench);
				if (stats != null) {
					labels.add(stats);
				}
			}
			if (!any) {
				throw new IngestException("Not a JMH JSON report: expected a non-empty array of benchmarks");
			}
			if (labels.isEmpty()) {
				throw new IngestException("No JMH benchmarks with a primaryMetric found");
			}
			return aggregate(labels);
		}
		catch (IngestException ex) {
			throw ex;
		}
		catch (RuntimeException ex) {
			throw new IngestException("Failed to parse JMH JSON report: " + ex.getMessage(), ex);
		}
	}

	private static PerfResults.LabelStats toLabelStats(JsonNode bench) {
		JsonNode metric = bench.path("primaryMetric");
		if (metric.isMissingNode()) {
			return null;
		}
		String label = label(bench);
		long samples = sampleCount(bench, metric);
		double score = metric.path("score").asDouble();
		String unit = metric.path("scoreUnit").asString("");
		String mode = bench.path("mode").asString("");
		JsonNode pcts = metric.path("scorePercentiles");

		if ("thrpt".equals(mode) || unit.startsWith("ops/")) {
			// Throughput: ops per <time unit> -> mean ms/op. Percentiles are an ops
			// distribution that does not invert to latency tails, so report them flat.
			double opsPerSec = score / unitInSeconds(timeUnitOf(unit));
			double meanMs = (opsPerSec > 0) ? (1000.0 / opsPerSec) : 0.0;
			return new PerfResults.LabelStats(label, samples, 0, 0.0, meanMs, meanMs, meanMs, meanMs, meanMs);
		}
		// Time-per-op: convert the score and each percentile from the score unit to ms.
		double f = msPerUnit(timeUnitOf(unit));
		double meanMs = score * f;
		return new PerfResults.LabelStats(label, samples, 0, 0.0, meanMs, pct(pcts, "50.0", score) * f,
				pct(pcts, "90.0", score) * f, pct(pcts, "95.0", score) * f, pct(pcts, "99.0", score) * f);
	}

	/** Label is {@code Class.method[k=v,...]}; params are sorted for a stable key. */
	private static String label(JsonNode bench) {
		String fqn = bench.path("benchmark").asString("benchmark");
		String shortName = fqn;
		int lastDot = fqn.lastIndexOf('.');
		if (lastDot > 0) {
			int prevDot = fqn.lastIndexOf('.', lastDot - 1);
			shortName = fqn.substring(prevDot + 1);
		}
		JsonNode params = bench.path("params");
		if (!params.isObject() || params.isEmpty()) {
			return shortName;
		}
		Map<String, String> sorted = new TreeMap<>();
		for (Map.Entry<String, JsonNode> e : params.properties()) {
			sorted.put(e.getKey(), e.getValue().asString(""));
		}
		StringBuilder sb = new StringBuilder(shortName).append('[');
		boolean first = true;
		for (Map.Entry<String, String> e : sorted.entrySet()) {
			if (!first) {
				sb.append(',');
			}
			sb.append(e.getKey()).append('=').append(e.getValue());
			first = false;
		}
		return sb.append(']').toString();
	}

	/**
	 * Samples = forks x measurement iterations; falls back to the rawData point count.
	 */
	private static long sampleCount(JsonNode bench, JsonNode metric) {
		long forks = bench.path("forks").asLong();
		long iterations = bench.path("measurementIterations").asLong();
		if (forks > 0 && iterations > 0) {
			return forks * iterations;
		}
		long raw = 0;
		JsonNode rawData = metric.path("rawData");
		if (rawData.isArray()) {
			for (JsonNode fork : rawData) {
				raw += fork.isArray() ? fork.size() : 1;
			}
		}
		return (raw > 0) ? raw : Math.max(1, iterations);
	}

	private static double pct(JsonNode pcts, String key, double fallback) {
		JsonNode n = pcts.path(key);
		return (n.isMissingNode() || n.isNull()) ? fallback : n.asDouble();
	}

	/**
	 * The time component of a JMH score unit ({@code us/op} -> {@code us}, {@code ops/s}
	 * -> {@code s}).
	 */
	private static String timeUnitOf(String scoreUnit) {
		int slash = scoreUnit.indexOf('/');
		if (slash < 0) {
			return scoreUnit.trim();
		}
		String left = scoreUnit.substring(0, slash).trim();
		String right = scoreUnit.substring(slash + 1).trim();
		return "ops".equals(left) ? right : left;
	}

	private static double msPerUnit(String timeUnit) {
		return switch (timeUnit) {
			case "ns" -> 1e-6;
			case "us", "µs" -> 1e-3;
			case "ms" -> 1.0;
			case "s" -> 1000.0;
			default -> 1.0;
		};
	}

	private static double unitInSeconds(String timeUnit) {
		return switch (timeUnit) {
			case "ns" -> 1e-9;
			case "us", "µs" -> 1e-6;
			case "ms" -> 1e-3;
			case "m" -> 60.0;
			default -> 1.0;
		};
	}

	/**
	 * Sample-weighted roll-up across benchmarks; per-benchmark labels are the real
	 * signal.
	 */
	private static PerfResults aggregate(List<PerfResults.LabelStats> labels) {
		long total = 0;
		double wMean = 0;
		double wP50 = 0;
		double wP90 = 0;
		double wP95 = 0;
		double wP99 = 0;
		double min = Double.MAX_VALUE;
		double max = 0;
		for (PerfResults.LabelStats l : labels) {
			long n = Math.max(1, l.sampleCount());
			total += n;
			wMean += l.meanMs() * n;
			wP50 += l.p50Ms() * n;
			wP90 += l.p90Ms() * n;
			wP95 += l.p95Ms() * n;
			wP99 += l.p99Ms() * n;
			min = Math.min(min, l.meanMs());
			max = Math.max(max, l.meanMs());
		}
		double w = (total > 0) ? total : 1;
		return new PerfResults(total, 0, 0.0, 0.0, 0, wMean / w, wP50 / w, wP90 / w, wP95 / w, wP99 / w,
				(min == Double.MAX_VALUE) ? 0 : min, max, labels);
	}

}
