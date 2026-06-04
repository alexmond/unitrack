package io.github.alexmond.unitrack.repository;

import io.github.alexmond.unitrack.domain.CoverageReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CoverageReportRepository extends JpaRepository<CoverageReport, Long> {

    Optional<CoverageReport> findByRunId(Long runId);
}
