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

	/**
	 * Per-label latency accumulator backed by an {@link Histogram} (3 significant digits,
	 * auto-resizing) — O(1) memory regardless of sample count, so a multi-hour run with
	 * millions of samples no longer grows an unbounded list.
	 */
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
