package org.alexmond.unitrack.repository;

import org.alexmond.unitrack.domain.TestSuiteResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestSuiteResultRepository extends JpaRepository<TestSuiteResult, Long> {

	List<TestSuiteResult> findByRunIdOrderByNameAsc(Long runId);

}
