package org.alexmond.unitrack.web.ui;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.TestRun;
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

	private final RunReportAssembler assembler;

	private final ProjectAccessService access;

	@GetMapping("/runs/{id}/export")
	public String export(@PathVariable Long id, Model model) {
		TestRun run = access.requireReadRun(id);
		assembler.populate(model, run);
		return "run-export";
	}

}
