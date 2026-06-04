package io.github.alexmond.unitrack.repository;

import io.github.alexmond.unitrack.domain.TestSuiteResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestSuiteResultRepository extends JpaRepository<TestSuiteResult, Long> {

    List<TestSuiteResult> findByRunIdOrderByNameAsc(Long runId);
}
