package org.alexmond.unitrack.repository;

import org.alexmond.unitrack.domain.CoverageReport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CoverageReportRepository extends JpaRepository<CoverageReport, Long> {

	Optional<CoverageReport> findByRunId(Long runId);

	/** Coverage reports for a project, newest run first (take the first for "latest"). */
	@Query("select c from CoverageReport c where c.run.project.id = :projectId order by c.run.createdAt desc")
	List<CoverageReport> findLatestForProject(Long projectId, Pageable pageable);

}
