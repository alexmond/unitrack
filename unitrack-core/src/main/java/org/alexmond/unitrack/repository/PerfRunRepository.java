package org.alexmond.unitrack.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.alexmond.unitrack.domain.PerfRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PerfRunRepository extends JpaRepository<PerfRun, Long> {

	/** Distinct flags (series) a project has perf runs for — for the perf flag filter. */
	@Query("select distinct r.flag from PerfRun r where r.project.id = ?1 order by r.flag")
	List<String> findDistinctFlagsByProjectId(Long projectId);

	/**
	 * The perf run just before this one in the same project+branch+flag series (←/→ nav).
	 */
	@Query("select r from PerfRun r where r.project.id = :pid and r.flag = :flag "
			+ "and ((:branch is null and r.branch is null) or r.branch = :branch) and r.createdAt < :ts "
			+ "order by r.createdAt desc, r.id desc")
	List<PerfRun> findPrevious(@org.springframework.data.repository.query.Param("pid") Long pid,
			@org.springframework.data.repository.query.Param("branch") String branch,
			@org.springframework.data.repository.query.Param("flag") String flag,
			@org.springframework.data.repository.query.Param("ts") Instant ts, Pageable pageable);

	/**
	 * The perf run just after this one in the same project+branch+flag series (←/→ nav).
	 */
	@Query("select r from PerfRun r where r.project.id = :pid and r.flag = :flag "
			+ "and ((:branch is null and r.branch is null) or r.branch = :branch) and r.createdAt > :ts "
			+ "order by r.createdAt asc, r.id asc")
	List<PerfRun> findNext(@org.springframework.data.repository.query.Param("pid") Long pid,
			@org.springframework.data.repository.query.Param("branch") String branch,
			@org.springframework.data.repository.query.Param("flag") String flag,
			@org.springframework.data.repository.query.Param("ts") Instant ts, Pageable pageable);

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
	 * Newest first with a stable id tiebreak — for ordered series (change-point
	 * detection).
	 */
	List<PerfRun> findByProjectIdAndFlagOrderByCreatedAtDescIdDesc(Long projectId, String flag, Pageable pageable);

	/**
	 * Most recent prior perf run on the baseline branch with the same flag — the
	 * baseline.
	 */
	Optional<PerfRun> findFirstByProjectIdAndBranchAndFlagAndIdNotAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
			Long projectId, String branch, String flag, Long excludeId, Instant createdAt);

}
