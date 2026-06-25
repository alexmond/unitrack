package org.alexmond.unitrack.repository;

import org.alexmond.unitrack.domain.TestSuiteResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TestSuiteResultRepository extends JpaRepository<TestSuiteResult, Long> {

	List<TestSuiteResult> findByRunIdOrderByNameAsc(Long runId);

	/** Bulk-delete all of a run's suites — for hard run deletion. */
	@Modifying
	@Query("delete from TestSuiteResult s where s.run.id = :runId")
	void deleteByRunId(@Param("runId") Long runId);

}
