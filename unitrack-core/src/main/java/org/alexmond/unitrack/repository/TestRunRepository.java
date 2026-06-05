package org.alexmond.unitrack.repository;

import org.alexmond.unitrack.domain.TestRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TestRunRepository extends JpaRepository<TestRun, Long> {

	List<TestRun> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);

	List<TestRun> findByProjectIdOrderByCreatedAtAsc(Long projectId, Pageable pageable);

	long countByProjectId(Long projectId);

	/**
	 * Most recent prior run on the baseline branch with the same flag — the gate
	 * baseline.
	 */
	Optional<TestRun> findFirstByProjectIdAndBranchAndFlagAndIdNotAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
			Long projectId, String branch, String flag, Long excludeId, Instant createdAt);

	@Query("select distinct t.flag from TestRun t where t.project.id = :projectId order by t.flag")
	List<String> findDistinctFlags(@Param("projectId") Long projectId);

	Optional<TestRun> findFirstByProjectIdAndFlagOrderByCreatedAtDesc(Long projectId, String flag);

	/** Existing run for a merge key, so sharded uploads accumulate into one run. */
	Optional<TestRun> findByProjectIdAndRunKey(Long projectId, String runKey);

}
