package org.alexmond.unitrack.repository;

import org.alexmond.unitrack.domain.TestRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestRunRepository extends JpaRepository<TestRun, Long> {

	List<TestRun> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);

	List<TestRun> findByProjectIdOrderByCreatedAtAsc(Long projectId, Pageable pageable);

	long countByProjectId(Long projectId);

}
