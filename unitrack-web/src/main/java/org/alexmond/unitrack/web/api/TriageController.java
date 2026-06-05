package org.alexmond.unitrack.web.api;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.report.ReportingService;
import org.alexmond.unitrack.report.TriageResult;
import org.alexmond.unitrack.report.TriageRuleView;
import org.alexmond.unitrack.report.TriageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for triage rules and run categorization. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TriageController {

	private final TriageService triage;

	private final ReportingService reporting;

	@GetMapping("/projects/{id}/triage-rules")
	public ResponseEntity<List<TriageRuleView>> rules(@PathVariable Long id) {
		if (reporting.findProject(id).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(triage.listRules(id));
	}

	@PostMapping("/projects/{id}/triage-rules")
	public ResponseEntity<TriageRuleView> addRule(@PathVariable Long id, @RequestBody RuleRequest req) {
		if (reporting.findProject(id).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		int priority = (req.priority() != null) ? req.priority() : 100;
		TriageRuleView view = triage.addRule(id, req.name(), req.category(), req.pattern(), priority);
		return ResponseEntity.status(HttpStatus.CREATED).body(view);
	}

	@DeleteMapping("/triage-rules/{ruleId}")
	public ResponseEntity<Void> deleteRule(@PathVariable Long ruleId) {
		triage.deleteRule(ruleId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/runs/{id}/triage")
	public ResponseEntity<TriageResult> triage(@PathVariable Long id) {
		if (reporting.findRun(id).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(triage.triageRun(id));
	}

	/** Body for creating a triage rule. */
	public record RuleRequest(String name, String category, String pattern, Integer priority) {
	}

}
