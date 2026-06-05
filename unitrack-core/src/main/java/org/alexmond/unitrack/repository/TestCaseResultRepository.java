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
