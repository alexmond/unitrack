package org.alexmond.unitrack.ingest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Parses a Gatling {@code simulation.log} (tab-separated). Each {@code REQUEST} line
 * carries a request name, a start and end epoch-millis timestamp, and an
 * {@code OK}/{@code
 * KO} status; latency is {@code end - start}. The field order shifts slightly between
 * Gatling versions, so this locates the pieces by shape (epoch-millis numbers, the OK/KO
 * token) rather than fixed columns. Per-request percentiles are computed from the
 * samples.
 */
@Component
public class GatlingLogParser implements PerfResultParser {

	private static final long EPOCH_MILLIS_MIN = 100_000_000_000L; // ~2001, i.e. a 12+
																	// digit timestamp

	@Override
	public String format() {
		return "gatling";
	}

	@Override
	public boolean supports(String headSample) {
		String h = headSample.stripLeading();
		return !h.startsWith("{") && !h.startsWith("<") && headSample.contains("REQUEST\t");
	}

	@Override
	public PerfResults parse(InputStream in) {
		Map<String, Acc> byLabel = new LinkedHashMap<>();
		long minStart = Long.MAX_VALUE;
		long maxEnd = Long.MIN_VALUE;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (!line.startsWith("REQUEST\t")) {
					continue;
				}
				String[] f = line.split("\t", -1);
				String name = requestName(f);
				long[] ts = timestamps(f);
				if (name == null || ts == null) {
					continue;
				}
				boolean ok = !containsKo(f);
				long latency = Math.max(0, ts[1] - ts[0]);
				byLabel.computeIfAbsent(name, (k) -> new Acc()).add(latency, ok);
				minStart = Math.min(minStart, ts[0]);
				maxEnd = Math.max(maxEnd, ts[1]);
			}
		}
		catch (java.io.IOException ex) {
			throw new IngestException("Failed to parse Gatling simulation.log: " + ex.getMessage(), ex);
		}
		if (byLabel.isEmpty()) {
			throw new IngestException("No REQUEST rows found in Gatling simulation.log");
		}
		Acc overall = new Acc();
		List<PerfResults.LabelStats> labels = new ArrayList<>();
		for (Map.Entry<String, Acc> e : byLabel.entrySet()) {
			overall.merge(e.getValue());
			labels.add(e.getValue().toLabelStats(e.getKey()));
		}
		long durationMs = (maxEnd > minStart) ? (maxEnd - minStart) : 0;
		double throughput = (durationMs > 0) ? (overall.count() * 1000.0 / durationMs) : 0.0;
		return overall.toResults(throughput, durationMs, labels);
	}

	/**
	 * The request name: first non-blank, non-numeric, non-status field after
	 * {@code REQUEST}.
	 */
	private static String requestName(String[] f) {
		for (int i = 1; i < f.length; i++) {
			String v = f[i].strip();
			if (!v.isEmpty() && !isLong(v) && !"OK".equals(v) && !"KO".equals(v)) {
				return v;
			}
		}
		return null;
	}

	/** The two epoch-millis timestamps (start, end) found in the row. */
	private static long[] timestamps(String[] f) {
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		int found = 0;
		for (String v : f) {
			String s = v.strip();
			if (isLong(s)) {
				long n = Long.parseLong(s);
				if (n >= EPOCH_MILLIS_MIN) {
					min = Math.min(min, n);
					max = Math.max(max, n);
					found++;
				}
			}
		}
		return (found >= 2) ? new long[] { min, max } : null;
	}

	private static boolean containsKo(String[] f) {
		for (String v : f) {
			if ("KO".equals(v.strip())) {
				return true;
			}
		}
		return false;
	}

	private static boolean isLong(String s) {
		if (s.isEmpty()) {
			return false;
		}
		for (int i = 0; i < s.length(); i++) {
			if (!Character.isDigit(s.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	/** Per-label accumulator of sample latencies (nearest-rank percentiles). */
	private static final class Acc {

		private final List<Long> latencies = new ArrayList<>();

		private long errors;

		void add(long ms, boolean ok) {
			this.latencies.add(ms);
			if (!ok) {
				this.errors++;
			}
		}

		void merge(Acc other) {
			this.latencies.addAll(other.latencies);
			this.errors += other.errors;
		}

		long count() {
			return this.latencies.size();
		}

		PerfResults.LabelStats toLabelStats(String label) {
			long[] s = sorted();
			return new PerfResults.LabelStats(label, s.length, this.errors, errorPct(s.length), mean(s), pct(s, 50),
					pct(s, 90), pct(s, 95), pct(s, 99));
		}

		PerfResults toResults(double throughput, long durationMs, List<PerfResults.LabelStats> labels) {
			long[] s = sorted();
			return new PerfResults(s.length, this.errors, errorPct(s.length), throughput, durationMs, mean(s),
					pct(s, 50), pct(s, 90), pct(s, 95), pct(s, 99), (s.length > 0) ? s[0] : 0,
					(s.length > 0) ? s[s.length - 1] : 0, labels);
		}

		private long[] sorted() {
			long[] s = this.latencies.stream().mapToLong(Long::longValue).toArray();
			Arrays.sort(s);
			return s;
		}

		private double errorPct(long total) {
			return (total > 0) ? (this.errors * 100.0 / total) : 0.0;
		}

		private static double mean(long[] s) {
			if (s.length == 0) {
				return 0.0;
			}
			long sum = 0;
			for (long v : s) {
				sum += v;
			}
			return (double) sum / s.length;
		}

		private static double pct(long[] sorted, double p) {
			if (sorted.length == 0) {
				return 0.0;
			}
			int rank = (int) Math.ceil(p / 100.0 * sorted.length);
			rank = Math.max(1, Math.min(rank, sorted.length));
			return sorted[rank - 1];
		}

	}

}
