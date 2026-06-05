package org.alexmond.unitrack.repository;

import java.util.List;
import java.util.Optional;

import org.alexmond.unitrack.domain.FlakyTest;
import org.alexmond.unitrack.report.FlakyStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlakyTestRepository extends JpaRepository<FlakyTest, Long> {

	List<FlakyTest> findByProjectId(Long projectId);

	@Query("""
			select f from FlakyTest f
			where f.project.id = :projectId
			  and coalesce(f.className, '') = coalesce(:className, '')
			  and f.name = :name
			""")
	Optional<FlakyTest> findOne(@Param("projectId") Long projectId, @Param("className") String className,
			@Param("name") String name);

	/**
	 * Detects flaky tests for a project: tests that, within a single commit, were
	 * observed both passing and failing across runs. Aggregates per-test metrics in the
	 * same pass.
	 */
	@Query(nativeQuery = true, value = """
			SELECT per.class_name AS className,
			       per.name AS name,
			       SUM(per.passed_c * per.failed_c) AS flakyCommits,
			       SUM(per.total_c) AS totalResults,
			       SUM(per.fail_c) AS failures,
			       MAX(per.last_fail) AS lastFailureAt
			FROM (
			    SELECT tc.class_name AS class_name,
			           tc.name AS name,
			           tr.commit_sha AS commit_sha,
			           MAX(CASE WHEN tc.status = 'PASSED' THEN 1 ELSE 0 END) AS passed_c,
			           MAX(CASE WHEN tc.status IN ('FAILED', 'ERROR') THEN 1 ELSE 0 END) AS failed_c,
			           COUNT(*) AS total_c,
			           SUM(CASE WHEN tc.status IN ('FAILED', 'ERROR') THEN 1 ELSE 0 END) AS fail_c,
			           MAX(CASE WHEN tc.status IN ('FAILED', 'ERROR') THEN tr.created_at END) AS last_fail
			    FROM test_case_result tc
			    JOIN test_run tr ON tr.id = tc.run_id
			    WHERE tr.project_id = :projectId AND tr.commit_sha IS NOT NULL
			    GROUP BY tc.class_name, tc.name, tr.commit_sha
			) per
			GROUP BY per.class_name, per.name
			HAVING SUM(per.passed_c * per.failed_c) > 0
			ORDER BY SUM(per.fail_c) DESC, SUM(per.passed_c * per.failed_c) DESC
			""")
	List<FlakyStat> findFlakyStats(@Param("projectId") Long projectId);

}
