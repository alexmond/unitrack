package org.alexmond.unitrack.repository;

import org.alexmond.unitrack.domain.AuditEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

	List<AuditEntry> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);

	List<AuditEntry> findByOrderByCreatedAtDesc(Pageable pageable);

}
