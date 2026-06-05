package org.alexmond.unitrack.web.ui;

import org.alexmond.unitrack.domain.CoverageReport;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.FailureClusteringService;
import org.alexmond.unitrack.report.QualityGateService;
import org.alexmond.unitrack.report.ReportingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Server-rendered dashboard (Thymeleaf). */
@Controller
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardController {

	private static final int RUN_LIST_LIMIT = 50;

	private static final int TREND_LIMIT = 30;

	private static final int COVERAGE_FILE_LIMIT = 200;

	private final ReportingService reporting;

	private final QualityGateService qualityGate;

	private final FailureClusteringService clustering;

	@GetMapping("/")
	public String index(Model model) {
		List<Project> projects = reporting.listProjects();
		Map<Long, Long> runCounts = new HashMap<>();
		Map<Long, TestRun> latest = new HashMap<>();
		for (Project p : projects) {
			runCounts.put(p.getId(), reporting.runCount(p.getId()));
			reporting.recentRuns(p.getId(), 1).stream().findFirst().ifPresent((run) -> latest.put(p.getId(), run));
		}
		model.addAttribute("projects", projects);
		model.addAttribute("runCounts", runCounts);
		model.addAttribute("latest", latest);
		return "index";
	}

	@GetMapping("/projects/{id}")
	public String project(@PathVariable Long id, Model model) {
		Project project = reporting.findProject(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
		List<TestRun> runs = reporting.recentRuns(id, RUN_LIST_LIMIT);
		List<TestRun> trend = reporting.trendRuns(id, TREND_LIMIT);

		model.addAttribute("project", project);
		model.addAttribute("runs", runs);
		model.addAttribute("flags", reporting.flagSummaries(id));
		model.addAttribute("trendLabels", toJson(trend.stream().map(TestRun::getShortSha).toList()));
		model.addAttribute("trendPassRate", toJson(trend.stream().map((r) -> round(r.passRate())).toList()));
		model.addAttribute("trendCoverage", toJson(trend.stream().map(TestRun::getLineCoveragePct).toList()));
		return "project";
	}

	@GetMapping("/projects/{id}/clusters")
	public String clusters(@PathVariable Long id, Model model) {
		Project project = reporting.findProject(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
		model.addAttribute("project", project);
		model.addAttribute("clusters", clustering.cluster(id));
		return "clusters";
	}

	@GetMapping("/runs/{id}")
	public String run(@PathVariable Long id, Model model) {
		TestRun run = reporting.findRun(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
		model.addAttribute("run", run);
		model.addAttribute("gate", qualityGate.evaluate(id).orElse(null));
		model.addAttribute("suites", reporting.suitesFor(id));
		model.addAttribute("failures", reporting.failedCasesFor(id));

		Optional<CoverageReport> coverage = reporting.coverageFor(id);
		model.addAttribute("coverage", coverage.orElse(null));
		List<?> files = coverage.map((c) -> reporting.coverageFiles(c.getId(), COVERAGE_FILE_LIMIT)).orElse(List.of());
		model.addAttribute("coverageFiles", files);
		return "run";
	}

	/** Serializes a list of Strings/Numbers/nulls to a JSON array for the trend chart. */
	private static String toJson(List<?> values) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				sb.append(',');
			}
			Object v = values.get(i);
			if (v == null) {
				sb.append("null");
			}
			else if (v instanceof Number) {
				sb.append(v);
			}
			else {
				sb.append('"').append(escape(v.toString())).append('"');
			}
		}
		return sb.append(']').toString();
	}

	private static String escape(String s) {
		StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (c < 0x20) {
						out.append(String.format("\\u%04x", (int) c));
					}
					else {
						out.append(c);
					}
				}
			}
		}
		return out.toString();
	}

	private static double round(double value) {
		return Math.round(value * 10.0) / 10.0;
	}

}
