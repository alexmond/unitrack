package org.alexmond.unitrack.repository;

import java.util.Collection;
import java.util.List;

import org.alexmond.unitrack.domain.IngestJob;
import org.alexmond.unitrack.domain.IngestStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestJobRepository extends JpaRepository<IngestJob, Long> {

	List<IngestJob> findByOrderByCreatedAtDesc(Pageable pageable);

	List<IngestJob> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);

	List<IngestJob> findByStatusIn(Collection<IngestStatus> statuses);

}
