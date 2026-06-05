package org.alexmond.unitrack.repository;

import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestCaseResultRepository extends JpaRepository<TestCaseResult, Long> {

	List<TestCaseResult> findByRunIdOrderByStatusAscClassNameAscNameAsc(Long runId);

	List<TestCaseResult> findByRunIdAndStatusInOrderByClassNameAscNameAsc(Long runId, List<TestStatus> statuses);

}
