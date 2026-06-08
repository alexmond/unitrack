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
 * Parses a JMeter results file (JTL, CSV flavour). JTL stores one row per sample, so this
 * collects every {@code elapsed} value per label and computes the percentiles itself.
 * Columns are located by header name (order is not guaranteed); a UTF-8 BOM is tolerated.
 */
@Component
public class JmeterJtlParser implements PerfResultParser {

	@Override
	public String format() {
		return "jmeter";
	}

	@Override
	public boolean supports(String headSample) {
		String h = headSample.stripLeading();
		return !h.startsWith("{") && !h.startsWith("<") && h.contains("elapsed") && h.contains("label")
				&& h.contains("timeStamp");
	}

	@Override
	public PerfResults parse(InputStream in) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String header = reader.readLine();
			if (header == null) {
				throw new IngestException("Empty JMeter JTL file");
			}
			List<String> cols = splitCsv(stripBom(header));
			int tsIdx = indexOf(cols, "timeStamp");
			int elapsedIdx = indexOf(cols, "elapsed");
			int labelIdx = indexOf(cols, "label");
			int successIdx = indexOf(cols, "success");
			if (elapsedIdx < 0 || labelIdx < 0) {
				throw new IngestException("Not a JMeter JTL CSV: missing 'elapsed'/'label' columns");
			}

			Map<String, Acc> byLabel = new LinkedHashMap<>();
			long minStart = Long.MAX_VALUE;
			long maxEnd = Long.MIN_VALUE;
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				List<String> f = splitCsv(line);
				if (elapsedIdx >= f.size() || labelIdx >= f.size()) {
					continue;
				}
				long elapsed = parseLong(f.get(elapsedIdx));
				boolean ok = successIdx < 0 || successIdx >= f.size() || !"false".equalsIgnoreCase(f.get(successIdx));
				byLabel.computeIfAbsent(f.get(labelIdx), (k) -> new Acc()).add(elapsed, ok);
				if (tsIdx >= 0 && tsIdx < f.size()) {
					long ts = parseLong(f.get(tsIdx));
					minStart = Math.min(minStart, ts);
					maxEnd = Math.max(maxEnd, ts + elapsed);
				}
			}

			Acc overall = new Acc();
			List<PerfResults.LabelStats> labels = new ArrayList<>();
			for (Map.Entry<String, Acc> e : byLabel.entrySet()) {
				Acc a = e.getValue();
				overall.merge(a);
				labels.add(a.toLabelStats(e.getKey()));
			}
			long durationMs = (maxEnd > minStart) ? (maxEnd - minStart) : 0;
			double throughput = (durationMs > 0) ? (overall.count() * 1000.0 / durationMs) : 0.0;
			return overall.toResults(throughput, durationMs, labels);
		}
		catch (IngestException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IngestException("Failed to parse JMeter JTL: " + ex.getMessage(), ex);
		}
	}

	private static int indexOf(List<String> cols, String name) {
		for (int i = 0; i < cols.size(); i++) {
			if (cols.get(i).equalsIgnoreCase(name)) {
				return i;
			}
		}
		return -1;
	}

	private static String stripBom(String s) {
		return (!s.isEmpty() && s.charAt(0) == '﻿') ? s.substring(1) : s;
	}

	private static long parseLong(String value) {
		try {
			return Long.parseLong(value.trim());
		}
		catch (NumberFormatException ex) {
			return 0L;
		}
	}

	/** Splits a CSV line, honouring double-quoted fields with {@code ""} escapes. */
	private static List<String> splitCsv(String line) {
		List<String> out = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		boolean inQuotes = false;
		int i = 0;
		while (i < line.length()) {
			char c = line.charAt(i);
			if (inQuotes) {
				if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					cur.append('"');
					i++;
				}
				else if (c == '"') {
					inQuotes = false;
				}
				else {
					cur.append(c);
				}
			}
			else if (c == '"') {
				inQuotes = true;
			}
			else if (c == ',') {
				out.add(cur.toString());
				cur.setLength(0);
			}
			else {
				cur.append(c);
			}
			i++;
		}
		out.add(cur.toString());
		return out;
	}

	/** Per-label accumulator of sample latencies. */
	private static final class Acc {

		private final List<Long> elapsed = new ArrayList<>();

		private long errors;

		void add(long ms, boolean ok) {
			this.elapsed.add(ms);
			if (!ok) {
				this.errors++;
			}
		}

		void merge(Acc other) {
			this.elapsed.addAll(other.elapsed);
			this.errors += other.errors;
		}

		long count() {
			return this.elapsed.size();
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
			long[] s = this.elapsed.stream().mapToLong(Long::longValue).toArray();
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

		/** Nearest-rank percentile on a sorted array. */
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
