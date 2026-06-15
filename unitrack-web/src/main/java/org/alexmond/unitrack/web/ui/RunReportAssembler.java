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
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/**
 * Fills the model for the self-contained run report ({@code run-export} template), shared
 * by the authenticated export route and the public share-link view.
 */
@Component
@RequiredArgsConstructor
public class RunReportAssembler {

	private static final int COVERAGE_FILE_LIMIT = 200;

	private final ReportingService reporting;

	private final QualityGateService qualityGate;

	private final TriageService triage;

	public void populate(Model model, TestRun run) {
		List<TestCaseResult> failures = reporting.failedCasesFor(run.getId());
		Optional<CoverageReport> coverage = reporting.coverageFor(run.getId());
		model.addAttribute("run", run);
		model.addAttribute("gate", qualityGate.evaluate(run.getId()).orElse(null));
		model.addAttribute("suites", reporting.suitesFor(run.getId()));
		model.addAttribute("failures", failures);
		model.addAttribute("categories", triage.categoryByCaseId(run.getProject().getId(), failures));
		model.addAttribute("coverage", coverage.orElse(null));
		model.addAttribute("coverageFiles",
				coverage.map((c) -> reporting.coverageFiles(c.getId(), COVERAGE_FILE_LIMIT)).orElse(List.of()));
	}

}
