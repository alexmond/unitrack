package org.alexmond.unitrack.ingest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.HdrHistogram.Histogram;
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

	/** Per-label latency accumulator backed by an {@link Histogram} — O(1) memory. */
	private static final class Acc {

		private final Histogram hist = new Histogram(3);

		private long errors;

		void add(long ms, boolean ok) {
			this.hist.recordValue(Math.max(0L, ms));
			if (!ok) {
				this.errors++;
			}
		}

		void merge(Acc other) {
			this.hist.add(other.hist);
			this.errors += other.errors;
		}

		long count() {
			return this.hist.getTotalCount();
		}

		PerfResults.LabelStats toLabelStats(String label) {
			long n = this.hist.getTotalCount();
			return new PerfResults.LabelStats(label, n, this.errors, errorPct(n), this.hist.getMean(),
					this.hist.getValueAtPercentile(50), this.hist.getValueAtPercentile(90),
					this.hist.getValueAtPercentile(95), this.hist.getValueAtPercentile(99));
		}

		PerfResults toResults(double throughput, long durationMs, List<PerfResults.LabelStats> labels) {
			long n = this.hist.getTotalCount();
			return new PerfResults(n, this.errors, errorPct(n), throughput, durationMs, this.hist.getMean(),
					this.hist.getValueAtPercentile(50), this.hist.getValueAtPercentile(90),
					this.hist.getValueAtPercentile(95), this.hist.getValueAtPercentile(99),
					(n > 0) ? this.hist.getMinValue() : 0, (n > 0) ? this.hist.getMaxValue() : 0, labels);
		}

		private double errorPct(long total) {
			return (total > 0) ? (this.errors * 100.0 / total) : 0.0;
		}

	}

}
