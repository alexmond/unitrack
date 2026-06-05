package org.alexmond.unitrack.repository;

import org.alexmond.unitrack.domain.CoverageReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CoverageReportRepository extends JpaRepository<CoverageReport, Long> {

	Optional<CoverageReport> findByRunId(Long runId);

}
