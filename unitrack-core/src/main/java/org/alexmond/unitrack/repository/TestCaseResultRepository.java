package org.alexmond.unitrack.repository;

import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TestCaseResultRepository extends JpaRepository<TestCaseResult, Long> {

	List<TestCaseResult> findByRunIdOrderByStatusAscClassNameAscNameAsc(Long runId);

	List<TestCaseResult> findByRunIdAndStatusInOrderByClassNameAscNameAsc(Long runId, List<TestStatus> statuses);

	/** Slowest cases in a run first — for the slowest-tests leaderboard. */
	List<TestCaseResult> findByRunIdOrderByDurationMsDescNameAsc(Long runId, Pageable pageable);

	/**
	 * One test's history across a project's runs (newest first) — for the duration trend.
	 */
	@Query("""
			select c from TestCaseResult c
			join fetch c.run r
			where r.project.id = :projectId and c.className = :className and c.name = :name
			order by r.createdAt desc
			""")
	List<TestCaseResult> findTestHistory(@Param("projectId") Long projectId, @Param("className") String className,
			@Param("name") String name, Pageable pageable);

	/**
	 * One test's history scoped to a single flag (newest first). A split-by-module
	 * project runs the same test in its module run AND in the {@code default} rollup, so
	 * the unscoped history returns two points per commit; scoping to the rollup flag
	 * gives one coherent series.
	 */
	@Query("""
			select c from TestCaseResult c
			join fetch c.run r
			where r.project.id = :projectId and c.className = :className and c.name = :name
			  and r.flag = :flag
			order by r.createdAt desc
			""")
	List<TestCaseResult> findTestHistoryForFlag(@Param("projectId") Long projectId,
			@Param("className") String className, @Param("name") String name, @Param("flag") String flag,
			Pageable pageable);

	/**
	 * One test's history on a single branch (newest first) — for first-failing-commit
	 * blame.
	 */
	@Query("""
			select c from TestCaseResult c
			join fetch c.run r
			where r.project.id = :projectId and r.branch = :branch
			  and c.className = :className and c.name = :name
			order by r.createdAt desc
			""")
	List<TestCaseResult> findTestHistoryOnBranch(@Param("projectId") Long projectId, @Param("branch") String branch,
			@Param("className") String className, @Param("name") String name, Pageable pageable);

	/**
	 * Recent failing/erroring cases for a project (newest first), for failure clustering.
	 */
	@Query("""
			select c from TestCaseResult c
			join fetch c.run r
			where r.project.id = :projectId and c.status in :statuses
			order by r.createdAt desc
			""")
	List<TestCaseResult> findRecentFailures(@Param("projectId") Long projectId,
			@Param("statuses") List<TestStatus> statuses, Pageable pageable);

}
