package org.alexmond.unitrack.ingest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Parses {@code go test -bench} text output as a performance source (the Go analog of
 * JMH). Each benchmark line — {@code BenchmarkName-8   1000000   123 ns/op   ...} — maps
 * to one {@link PerfResults.LabelStats}: {@code ns/op} → mean ms (lower is better), the
 * iteration count is the sample count. Go reports only a mean, so percentiles are flat.
 */
@Component
public class GoBenchParser implements PerfResultParser {

	// BenchmarkName-<cpus> <iters> <ns/op> ns/op [ ... B/op ... allocs/op ]
	private static final Pattern BENCH = Pattern
		.compile("^(Benchmark\\S+)\\s+(\\d+)\\s+([0-9]+(?:\\.[0-9]+)?)\\s+ns/op");

	@Override
	public String format() {
		return "go-bench";
	}

	@Override
	public boolean supports(String headSample) {
		return headSample.contains("ns/op") && headSample.contains("Benchmark");
	}

	@Override
	public PerfResults parse(InputStream in) {
		List<PerfResults.LabelStats> labels = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				Matcher m = BENCH.matcher(line.strip());
				if (!m.find()) {
					continue;
				}
				String label = m.group(1);
				long iterations = Long.parseLong(m.group(2));
				double meanMs = Double.parseDouble(m.group(3)) / 1_000_000.0;
				labels
					.add(new PerfResults.LabelStats(label, iterations, 0, 0.0, meanMs, meanMs, meanMs, meanMs, meanMs));
			}
		}
		catch (RuntimeException | java.io.IOException ex) {
			throw new IngestException("Failed to parse go test -bench output: " + ex.getMessage(), ex);
		}
		if (labels.isEmpty()) {
			throw new IngestException("No 'Benchmark... ns/op' lines found in go test -bench output");
		}
		return aggregate(labels);
	}

	/**
	 * Sample-weighted roll-up across benchmarks; per-benchmark labels are the real
	 * signal.
	 */
	private static PerfResults aggregate(List<PerfResults.LabelStats> labels) {
		long total = 0;
		double wMean = 0;
		double min = Double.MAX_VALUE;
		double max = 0;
		for (PerfResults.LabelStats l : labels) {
			long n = Math.max(1, l.sampleCount());
			total += n;
			wMean += l.meanMs() * n;
			min = Math.min(min, l.meanMs());
			max = Math.max(max, l.meanMs());
		}
		double w = (total > 0) ? total : 1;
		double mean = wMean / w;
		return new PerfResults(total, 0, 0.0, 0.0, 0, mean, mean, mean, mean, mean, (min == Double.MAX_VALUE) ? 0 : min,
				max, labels);
	}

}
