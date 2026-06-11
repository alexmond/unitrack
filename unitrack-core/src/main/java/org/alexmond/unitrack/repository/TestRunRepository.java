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

	/** Recent runs on a single branch, newest first — for branch-scoped Overview. */
	List<TestRun> findByProjectIdAndBranchOrderByCreatedAtDesc(Long projectId, String branch, Pageable pageable);

	long countByProjectId(Long projectId);

	/**
	 * Most recent prior run on the baseline branch with the same flag — the gate
	 * baseline.
	 */
	Optional<TestRun> findFirstByProjectIdAndBranchAndFlagAndIdNotAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
			Long projectId, String branch, String flag, Long excludeId, Instant createdAt);

	@Query("select distinct t.flag from TestRun t where t.project.id = :projectId order by t.flag")
	List<String> findDistinctFlags(@Param("projectId") Long projectId);

	/**
	 * Distinct branches seen for a project (alphabetical) — for the Overview branch
	 * picker.
	 */
	@Query("select distinct t.branch from TestRun t "
			+ "where t.project.id = :projectId and t.branch is not null order by t.branch")
	List<String> findDistinctBranches(@Param("projectId") Long projectId);

	/** Pull/merge-request numbers seen for a project (newest number first). */
	@Query("select distinct t.prNumber from TestRun t "
			+ "where t.project.id = :projectId and t.prNumber is not null order by t.prNumber desc")
	List<Integer> findDistinctPrNumbers(@Param("projectId") Long projectId);

	/** All runs belonging to one pull/merge request, newest first (the PR timeline). */
	List<TestRun> findByProjectIdAndPrNumberOrderByCreatedAtDesc(Long projectId, Integer prNumber);

	Optional<TestRun> findFirstByProjectIdAndFlagOrderByCreatedAtDesc(Long projectId, String flag);

	/** Existing run for a merge key, so sharded uploads accumulate into one run. */
	Optional<TestRun> findByProjectIdAndRunKey(Long projectId, String runKey);

	/** Latest run for a commit (optionally scoped to a flag) — for CI gate lookups. */
	@Query("""
			select t from TestRun t
			where t.project.id = :projectId and t.commitSha = :commit and (:flag is null or t.flag = :flag)
			order by t.createdAt desc
			""")
	List<TestRun> findLatestByCommit(@Param("projectId") Long projectId, @Param("commit") String commit,
			@Param("flag") String flag, Pageable pageable);

	/** Latest run on a branch (optionally scoped to a flag) — for CI gate lookups. */
	@Query("""
			select t from TestRun t
			where t.project.id = :projectId and t.branch = :branch and (:flag is null or t.flag = :flag)
			order by t.createdAt desc
			""")
	List<TestRun> findLatestByBranch(@Param("projectId") Long projectId, @Param("branch") String branch,
			@Param("flag") String flag, Pageable pageable);

}
