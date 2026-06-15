package org.alexmond.unitrack.web.ui;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.CoverageReport;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.QualityGateService;
import org.alexmond.unitrack.report.ReportingService;
import org.alexmond.unitrack.report.TriageService;
import org.alexmond.unitrack.web.account.ProjectAccessService;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Self-contained, offline-friendly export of a single run: one HTML document with inlined
 * styles and no external requests, suitable for CI artifacts or printing to PDF (@media
 * print).
 */
@Controller
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RunExportController {

	private static final int COVERAGE_FILE_LIMIT = 200;

	private final ReportingService reporting;

	private final QualityGateService qualityGate;

	private final TriageService triage;

	private final ProjectAccessService access;

	@GetMapping("/runs/{id}/export")
	public String export(@PathVariable Long id, Model model) {
		TestRun run = access.requireReadRun(id);
		List<TestCaseResult> failures = reporting.failedCasesFor(id);
		Optional<CoverageReport> coverage = reporting.coverageFor(id);
		model.addAttribute("run", run);
		model.addAttribute("gate", qualityGate.evaluate(id).orElse(null));
		model.addAttribute("suites", reporting.suitesFor(id));
		model.addAttribute("failures", failures);
		model.addAttribute("categories", triage.categoryByCaseId(run.getProject().getId(), failures));
		model.addAttribute("coverage", coverage.orElse(null));
		model.addAttribute("coverageFiles",
				coverage.map((c) -> reporting.coverageFiles(c.getId(), COVERAGE_FILE_LIMIT)).orElse(List.of()));
		return "run-export";
	}

}
