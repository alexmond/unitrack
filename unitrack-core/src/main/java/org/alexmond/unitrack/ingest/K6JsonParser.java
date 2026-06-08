package org.alexmond.unitrack.ingest;

import java.io.InputStream;
import java.util.List;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses a k6 end-of-test JSON summary ({@code k6 run --summary-export=summary.json}).
 * The summary is pre-aggregated, so this just reads the {@code http_req_duration}
 * percentiles, {@code http_reqs} count/rate and {@code http_req_failed} rate. No
 * per-label breakdown is available from the default summary.
 */
@Component
public class K6JsonParser implements PerfResultParser {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	@Override
	public String format() {
		return "k6";
	}

	@Override
	public boolean supports(String headSample) {
		String h = headSample.stripLeading();
		return h.startsWith("{") && headSample.contains("http_req_duration");
	}

	@Override
	public PerfResults parse(InputStream in) {
		try {
			JsonNode metrics = MAPPER.readTree(in).path("metrics");
			JsonNode dur = metrics.path("http_req_duration").path("values");
			JsonNode reqs = metrics.path("http_reqs").path("values");
			JsonNode failed = metrics.path("http_req_failed").path("values");

			long sampleCount = reqs.path("count").asLong();
			double throughput = reqs.path("rate").asDouble();
			double errorRate = failed.path("rate").asDouble();
			long errorCount = failed.has("fails") ? failed.path("fails").asLong() : Math.round(errorRate * sampleCount);
			long durationMs = (throughput > 0) ? Math.round(sampleCount / throughput * 1000.0) : 0L;

			return new PerfResults(sampleCount, errorCount, errorRate * 100.0, throughput, durationMs,
					dur.path("avg").asDouble(), dur.path("med").asDouble(), dur.path("p(90)").asDouble(),
					dur.path("p(95)").asDouble(), dur.path("p(99)").asDouble(), dur.path("min").asDouble(),
					dur.path("max").asDouble(), List.of());
		}
		catch (RuntimeException ex) {
			throw new IngestException("Failed to parse k6 JSON summary: " + ex.getMessage(), ex);
		}
	}

}
