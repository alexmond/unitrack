package org.alexmond.unitrack.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.alexmond.unitrack.domain.PerfRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerfRunRepository extends JpaRepository<PerfRun, Long> {

	List<PerfRun> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);

	/**
	 * Most recent prior perf run on the baseline branch with the same flag — the
	 * baseline.
	 */
	Optional<PerfRun> findFirstByProjectIdAndBranchAndFlagAndIdNotAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
			Long projectId, String branch, String flag, Long excludeId, Instant createdAt);

}
