package org.alexmond.unitrack.web.ui;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.web.ui.view.BreakdownRow;
import org.alexmond.unitrack.web.ui.view.BreakdownTable;
import org.alexmond.unitrack.web.ui.view.LatestRunLine;

/**
 * Formatting + view-model assembly shared by the analytics tabs (Tests, Test timing), so
 * the KPI deltas, latest-run line, breakdown links, and number formats read identically
 * on every tab. Pure functions — no Spring, no state.
 */
final class AnalyticsView {

	/** Latest-run line timestamp format (server zone). */
	private static final DateTimeFormatter RUN_TIME = DateTimeFormatter.ofPattern("MMM dd HH:mm")
		.withZone(ZoneId.systemDefault());

	/**
	 * Serializes the trend config; self-contained (the app exposes no ObjectMapper bean).
	 */
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private AnalyticsView() {
	}

	/** The latest-run line view for an analytics tab (null when there is no run). */
	static LatestRunLine latestRunLine(TestRun cur) {
		if (cur == null) {
			return null;
		}
		String label = (cur.getShortSha() != null && !cur.getShortSha().isBlank()) ? cur.getShortSha()
				: ("#" + cur.getId());
		return new LatestRunLine(cur.getId(), label, cur.getBranch(), RUN_TIME.format(cur.getCreatedAt()));
	}

	/** Assemble a by-group {@link BreakdownTable} (null when there are 0/1 groups). */
	static BreakdownTable moduleBreakdown(String selectedModule, List<BreakdownRow> rows, String title,
			List<String> columns) {
		return rows.isEmpty() ? null : new BreakdownTable(title, "latest run", columns, rows, selectedModule);
	}

	/** The URL for an analytics tab scoped to one module (breakdown row-click target). */
	static String scopeUrl(String page, Long id, String flag, String module) {
		return "/projects/" + id + "/" + page + "?flag=" + enc(flag) + "&module=" + enc(module);
	}

	private static String enc(String v) {
		return URLEncoder.encode(v, StandardCharsets.UTF_8);
	}

	/** One-decimal, US locale — for KPI values and deltas. */
	static String fmt1(double d) {
		return String.format(Locale.US, "%.1f", d);
	}

	/** {@link #fmt1} with an explicit leading "+" for non-negative values. */
	static String signed1(double d) {
		return ((d >= 0) ? "+" : "") + fmt1(d);
	}

	/** A signed integer delta with an explicit leading "+" for positive values. */
	static String signedL(long d) {
		return ((d > 0) ? "+" : "") + d;
	}

	/** Delta class where an increase is good (up), a decrease bad (down). */
	static String upIsGood(double d, double eps) {
		return (d > eps) ? "up" : ((d < -eps) ? "down" : "flat");
	}

	/** Delta class where an increase is bad (down), a decrease good (up). */
	static String upIsBad(double d, double eps) {
		return (d > eps) ? "down" : ((d < -eps) ? "up" : "flat");
	}

	/** One trend series descriptor for {@link #trendConfig} (primary Y axis). */
	static Map<String, Object> series(String label, String color, List<? extends Number> data) {
		Map<String, Object> s = new HashMap<>();
		s.put("label", label);
		s.put("color", color);
		s.put("data", data);
		return s;
	}

	/**
	 * A trend series on a specific axis ({@code "y"} or {@code "y2"} for the secondary
	 * axis).
	 */
	static Map<String, Object> series(String label, String color, List<? extends Number> data, String axis) {
		Map<String, Object> s = series(label, color, data);
		s.put("axis", axis);
		return s;
	}

	/** Epoch-millis of a run's creation, or null. */
	static Long epochMilli(TestRun run) {
		return (run.getCreatedAt() != null) ? run.getCreatedAt().toEpochMilli() : null;
	}

	/**
	 * Trend X labels for a run list, duplicate SHAs disambiguated (a re-run of the same
	 * commit).
	 */
	static List<String> trendLabelsFrom(List<TestRun> runs) {
		return labels(runs.stream().map(TestRun::getShortSha).toList());
	}

	/** Trend X labels with duplicate SHAs disambiguated. */
	static List<String> labels(List<String> shas) {
		Map<String, Integer> seen = new HashMap<>();
		List<String> out = new ArrayList<>(shas.size());
		for (String s : shas) {
			String label = (s == null || s.isBlank()) ? "—" : s;
			int n = seen.merge(label, 1, Integer::sum);
			out.add((n == 1) ? label : label + " ·" + n);
		}
		return out;
	}

	/**
	 * Assemble the JSON config for {@code window.__trendChart} (labels/runIds/times +
	 * per-series data + overlay/axis titles) so every tab's trend data shape lives in one
	 * place.
	 */
	static String trendConfig(List<String> labels, List<Long> runIds, List<Long> times,
			List<Map<String, Object>> series, Integer overlaySeries, String yTitle, String y2Title) {
		Map<String, Object> cfg = new HashMap<>();
		cfg.put("labels", labels);
		cfg.put("runIds", runIds);
		cfg.put("times", times);
		cfg.put("series", series);
		cfg.put("overlaySeries", overlaySeries);
		cfg.put("yTitle", yTitle);
		cfg.put("y2Title", y2Title);
		try {
			return MAPPER.writeValueAsString(cfg);
		}
		catch (JsonProcessingException ex) {
			return "{}";
		}
	}

}
