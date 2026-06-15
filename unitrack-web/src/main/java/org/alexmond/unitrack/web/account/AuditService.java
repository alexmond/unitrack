package org.alexmond.unitrack.web.account;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.AuditEntry;
import org.alexmond.unitrack.repository.AuditEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Records and reads the append-only audit trail of state-changing actions. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditService {

	private final AuditEntryRepository entries;

	/** Appends one audit entry; returns the persisted row. */
	@Transactional
	public AuditEntry record(String actor, String action, String source, Long projectId, String detail) {
		return this.entries.save(new AuditEntry(actor, action, source, projectId, detail));
	}

	public List<AuditEntry> recentForProject(Long projectId, int limit) {
		return this.entries.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, limit));
	}

	public List<AuditEntry> recent(int limit) {
		return this.entries.findByOrderByCreatedAtDesc(PageRequest.of(0, limit));
	}

}
