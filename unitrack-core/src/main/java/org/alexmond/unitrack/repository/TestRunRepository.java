package org.alexmond.unitrack.repository;

import org.alexmond.unitrack.domain.TestRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TestRunRepository extends JpaRepository<TestRun, Long> {

	List<TestRun> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);

	List<TestRun> findByProjectIdOrderByCreatedAtAsc(Long projectId, Pageable pageable);

	long countByProjectId(Long projectId);

	/**
	 * Most recent prior run on the baseline branch (excluding the given run) — the gate
	 * baseline.
	 */
	Optional<TestRun> findFirstByProjectIdAndBranchAndIdNotAndCreatedAtLessThanEqualOrderByCreatedAtDesc(Long projectId,
			String branch, Long excludeId, Instant createdAt);

}
