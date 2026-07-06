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

	/**
	 * The run just before this one in the same project+branch+flag series (for ←/→ nav).
	 */
	@Query("select r from TestRun r where r.project.id = :pid and r.flag = :flag "
			+ "and ((:branch is null and r.branch is null) or r.branch = :branch) and r.createdAt < :ts "
			+ "order by r.createdAt desc, r.id desc")
	List<TestRun> findPrevious(@Param("pid") Long pid, @Param("branch") String branch, @Param("flag") String flag,
			@Param("ts") Instant ts, Pageable pageable);

	/**
	 * The run just after this one in the same project+branch+flag series (for ←/→ nav).
	 */
	@Query("select r from TestRun r where r.project.id = :pid and r.flag = :flag "
			+ "and ((:branch is null and r.branch is null) or r.branch = :branch) and r.createdAt > :ts "
			+ "order by r.createdAt asc, r.id asc")
	List<TestRun> findNext(@Param("pid") Long pid, @Param("branch") String branch, @Param("flag") String flag,
			@Param("ts") Instant ts, Pageable pageable);

	/**
	 * The latest two runs of every project in one query — the board's batch fetch (avoids
	 * a per-project query). Returns rows for all projects that have runs; group by
	 * project in memory. The extra {@code rn} column is ignored by the entity mapping.
	 */
	@Query(nativeQuery = true, value = """
			SELECT z.* FROM (
			    SELECT t.*, ROW_NUMBER() OVER (PARTITION BY t.project_id ORDER BY t.created_at DESC, t.id DESC) AS rn
			    FROM test_run t
			) z
			WHERE z.rn <= 2
			""")
	List<TestRun> findLatestTwoRunsPerProject();

	/**
	 * For every project whose latest run is FAILED, how long it has been red — in one
	 * set-based query (no per-project N+1). The streak is scoped to the latest run's
	 * branch+flag: {@code lastGreenAt} = the most recent prior PASSED run on that series,
	 * {@code brokenSince} = the first run after it (the onset), {@code runsRed} = the
	 * runs shipped on top of the breakage. Projects whose latest run passed are not
	 * returned.
	 */
	@Query(nativeQuery = true, value = """
			WITH latest AS (
			    SELECT t.project_id, t.branch, t.flag, t.status, t.created_at,
			           ROW_NUMBER() OVER (PARTITION BY t.project_id ORDER BY t.created_at DESC, t.id DESC) AS rn
			    FROM test_run t
			),
			cur AS (
			    SELECT project_id, branch, flag, created_at AS latest_at
			    FROM latest WHERE rn = 1 AND status = 'FAILED'
			),
			grn AS (
			    SELECT project_id, last_green_at, last_green_id FROM (
			        SELECT c.project_id, g.created_at AS last_green_at, g.id AS last_green_id,
			               ROW_NUMBER() OVER (PARTITION BY c.project_id
			                                  ORDER BY g.created_at DESC, g.id DESC) AS grn_rn
			        FROM cur c
			        JOIN test_run g ON g.project_id = c.project_id
			             AND COALESCE(g.branch, '') = COALESCE(c.branch, '')
			             AND COALESCE(g.flag, '') = COALESCE(c.flag, '')
			             AND g.status = 'PASSED' AND g.created_at <= c.latest_at
			    ) gg WHERE grn_rn = 1
			)
			SELECT c.project_id AS "projectId",
			       g.last_green_at AS "lastGreenAt",
			       MIN(r.created_at) AS "brokenSince",
			       COUNT(r.id) AS "runsRed"
			FROM cur c
			LEFT JOIN grn g ON g.project_id = c.project_id
			JOIN test_run r ON r.project_id = c.project_id
			     AND COALESCE(r.branch, '') = COALESCE(c.branch, '')
			     AND COALESCE(r.flag, '') = COALESCE(c.flag, '')
			     AND r.created_at <= c.latest_at
			     AND (g.last_green_at IS NULL
			          OR r.created_at > g.last_green_at
			          OR (r.created_at = g.last_green_at AND r.id > g.last_green_id))
			GROUP BY c.project_id, g.last_green_at
			""")
	List<BrokenSince> findBrokenSince();

	/**
	 * The latest run of every branch of a project in one query — the branches list's
	 * batch fetch (avoids a per-branch latest-run query). The extra {@code rn} column is
	 * ignored by the entity mapping.
	 */
	@Query(nativeQuery = true, value = """
			SELECT z.* FROM (
			    SELECT t.*, ROW_NUMBER() OVER (PARTITION BY t.branch ORDER BY t.created_at DESC, t.id DESC) AS rn
			    FROM test_run t
			    WHERE t.project_id = :projectId AND t.branch IS NOT NULL
			) z
			WHERE z.rn = 1
			""")
	List<TestRun> findLatestRunPerBranch(@Param("projectId") Long projectId);

	/**
	 * Run count per branch for a project in one query — pairs with
	 * {@link #findLatestRunPerBranch}.
	 */
	@Query("select t.branch as branch, count(t) as cnt from TestRun t "
			+ "where t.project.id = :projectId and t.branch is not null group by t.branch")
	List<BranchRunCount> countRunsPerBranch(@Param("projectId") Long projectId);

	List<TestRun> findByProjectIdOrderByCreatedAtAsc(Long projectId, Pageable pageable);

	/** Recent runs on a single branch, newest first — for branch-scoped Overview. */
	List<TestRun> findByProjectIdAndBranchOrderByCreatedAtDesc(Long projectId, String branch, Pageable pageable);

	/**
	 * Recent runs of one flag, newest first — for a single-flag trend (split-by-module
	 * rollup).
	 */
	List<TestRun> findByProjectIdAndFlagOrderByCreatedAtDesc(Long projectId, String flag, Pageable pageable);

	/**
	 * Recent runs of one branch + flag, newest first — branch-scoped single-flag trend.
	 */
	List<TestRun> findByProjectIdAndBranchAndFlagOrderByCreatedAtDesc(Long projectId, String branch, String flag,
			Pageable pageable);

	long countByProjectId(Long projectId);

	long countByProjectIdAndBranch(Long projectId, String branch);

	/** Total runs with a given status — for the ops dashboard pass/fail breakdown. */
	long countByStatus(String status);

	/**
	 * Most recent non-passing runs across all projects — the ops "recent failures" list.
	 */
	List<TestRun> findTop20ByStatusNotOrderByCreatedAtDesc(String status);

	/**
	 * The run immediately before this one on the same branch + flag — for green→red
	 * muting.
	 */
	Optional<TestRun> findFirstByProjectIdAndBranchAndFlagAndCreatedAtLessThanOrderByCreatedAtDesc(Long projectId,
			String branch, String flag, Instant createdAt);

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

	/**
	 * Ids of every run on a branch — for hard-deleting a removed/expired branch (#400).
	 */
	@Query("select t.id from TestRun t where t.project.id = :projectId and t.branch = :branch")
	List<Long> findIdsByProjectIdAndBranch(@Param("projectId") Long projectId, @Param("branch") String branch);

	/** The id of a project's single most recent run — kept as a safety during expiry. */
	@Query("select t.id from TestRun t where t.project.id = :projectId order by t.createdAt desc limit 1")
	Long findLatestRunId(@Param("projectId") Long projectId);

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
