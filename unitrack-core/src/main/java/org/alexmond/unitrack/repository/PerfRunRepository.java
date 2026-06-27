package org.alexmond.unitrack.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.alexmond.unitrack.domain.PerfRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerfRunRepository extends JpaRepository<PerfRun, Long> {

	/**
	 * Whether the project has any perf/load-test runs — used to hide the empty Load tests
	 * tab.
	 */
	boolean existsByProjectId(Long projectId);

	List<PerfRun> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);

	/**
	 * Recent perf runs of one flag, newest first — for a single-flag trend
	 * (split-by-module).
	 */
	List<PerfRun> findByProjectIdAndFlagOrderByCreatedAtDesc(Long projectId, String flag, Pageable pageable);

	/**
	 * Most recent prior perf run on the baseline branch with the same flag — the
	 * baseline.
	 */
	Optional<PerfRun> findFirstByProjectIdAndBranchAndFlagAndIdNotAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
			Long projectId, String branch, String flag, Long excludeId, Instant createdAt);

}
