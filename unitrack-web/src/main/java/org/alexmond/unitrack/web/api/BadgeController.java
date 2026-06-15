package org.alexmond.unitrack.web.api;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.FlakyTestService;
import org.alexmond.unitrack.report.ReportingService;
import org.alexmond.unitrack.web.account.ProjectAccessService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/**
 * Embeddable status badges for a project's latest run — an SVG for READMEs and a
 * shields.io endpoint for JSON. Honors visibility: a private project a caller can't read
 * returns 404 so the badge can't be used to probe it (anonymous fetches only resolve
 * PUBLIC projects).
 */
@Controller
@RequiredArgsConstructor
public class BadgeController {

	// shields-style flat colors
	private static final String GREEN = "#4c1";

	private static final String YELLOW = "#dfb317";

	private static final String RED = "#e05d44";

	private static final String GREY = "#9f9f9f";

	private final ReportingService reporting;

	private final FlakyTestService flaky;

	private final ProjectAccessService access;

	@GetMapping(value = "/badge/{projectId}/{metric}.svg", produces = "image/svg+xml")
	@ResponseBody
	public ResponseEntity<String> svg(@PathVariable Long projectId, @PathVariable String metric) {
		Badge badge = badge(projectId, metric);
		return ResponseEntity.ok()
			.cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
			.contentType(MediaType.valueOf("image/svg+xml"))
			.body(renderSvg(badge));
	}

	@GetMapping(value = "/badge/{projectId}/{metric}", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ShieldsEndpoint json(@PathVariable Long projectId, @PathVariable String metric) {
		Badge badge = badge(projectId, metric);
		return new ShieldsEndpoint(1, badge.label(), badge.message(), badge.color());
	}

	private Badge badge(Long projectId, String metric) {
		Project project = reporting.findProject(projectId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
		if (!access.canRead(project)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
		}
		List<TestRun> recent = reporting.recentRuns(projectId, 1);
		TestRun latest = recent.isEmpty() ? null : recent.get(0);
		return switch (metric) {
			case "coverage" -> coverageBadge(latest);
			case "pass", "tests" -> passBadge(latest);
			case "flaky" -> flakyBadge(projectId);
			default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown metric: " + metric);
		};
	}

	private static Badge coverageBadge(TestRun latest) {
		Double cov = (latest != null) ? latest.getLineCoveragePct() : null;
		if (cov == null) {
			return new Badge("coverage", "n/a", GREY);
		}
		String color = (cov >= 80) ? GREEN : (cov >= 60) ? YELLOW : RED;
		return new Badge("coverage", String.format(Locale.ROOT, "%.0f%%", cov), color);
	}

	private static Badge passBadge(TestRun latest) {
		if (latest == null) {
			return new Badge("tests", "n/a", GREY);
		}
		double pass = latest.passRate();
		String color = (pass >= 100.0) ? GREEN : (pass >= 90.0) ? YELLOW : RED;
		return new Badge("tests", String.format(Locale.ROOT, "%.0f%% pass", pass), color);
	}

	private Badge flakyBadge(Long projectId) {
		long count = flaky.flakyCount(projectId);
		String color = (count == 0) ? GREEN : (count <= 3) ? YELLOW : RED;
		return new Badge("flaky", Long.toString(count), color);
	}

	/**
	 * Renders a minimal shields-style flat badge; widths are approximated from text
	 * length.
	 */
	private static String renderSvg(Badge badge) {
		String label = badge.label();
		String message = badge.message();
		int labelW = label.length() * 7 + 10;
		int msgW = message.length() * 7 + 10;
		int total = labelW + msgW;
		String aria = escape(label) + ": " + escape(message);
		return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + total + "\" height=\"20\" role=\"img\" "
				+ "aria-label=\"" + aria + "\">" + "<title>" + aria + "</title>" + "<rect rx=\"3\" width=\"" + total
				+ "\" height=\"20\" fill=\"#555\"/>" + "<rect rx=\"3\" x=\"" + labelW + "\" width=\"" + msgW
				+ "\" height=\"20\" fill=\"" + badge.color() + "\"/>" + "<g fill=\"#fff\" text-anchor=\"middle\" "
				+ "font-family=\"DejaVu Sans,Verdana,Geneva,sans-serif\" font-size=\"11\">" + "<text x=\""
				+ (labelW / 2) + "\" y=\"14\">" + escape(label) + "</text>" + "<text x=\"" + (labelW + msgW / 2)
				+ "\" y=\"14\">" + escape(message) + "</text>" + "</g></svg>";
	}

	private static String escape(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	private record Badge(String label, String message, String color) {
	}

	/** shields.io endpoint-badge schema. */
	public record ShieldsEndpoint(int schemaVersion, String label, String message, String color) {
	}

}
