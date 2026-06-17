package org.alexmond.unitrack.web.api;

import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.AuditEntry;
import org.alexmond.unitrack.web.account.AuditService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only API over the audit trail. Admin-only (enforced in {@code SecurityConfig};
 * {@code /api/v1/audit} requires {@code ROLE_ADMIN}). Returns the append-only history of
 * lifecycle events (e.g. {@code RUN_INGESTED}) and privileged actions, newest first.
 */
@RestController
@RequestMapping("/api/v1")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuditApiController {

	private static final int DEFAULT_LIMIT = 100;

	private static final int MAX_LIMIT = 500;

	private final AuditService audit;

	@GetMapping("/audit")
	public List<AuditEntryJson> audit(@RequestParam(required = false) Long project,
			@RequestParam(required = false) Integer limit) {
		int max = Math.min((limit != null && limit > 0) ? limit : DEFAULT_LIMIT, MAX_LIMIT);
		List<AuditEntry> entries = (project != null) ? this.audit.recentForProject(project, max)
				: this.audit.recent(max);
		return entries.stream().map(AuditEntryJson::of).toList();
	}

	/** One audit entry as stable JSON. */
	public record AuditEntryJson(Long id, String actor, String action, String source, Long projectId, String detail,
			Instant createdAt) {

		static AuditEntryJson of(AuditEntry e) {
			return new AuditEntryJson(e.getId(), e.getActor(), e.getAction(), e.getSource(), e.getProjectId(),
					e.getDetail(), e.getCreatedAt());
		}
	}

}
