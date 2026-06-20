package org.alexmond.unitrack.repository;

import org.alexmond.unitrack.domain.CoverageFileEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CoverageFileEntryRepository extends JpaRepository<CoverageFileEntry, Long> {

	List<CoverageFileEntry> findByReportIdOrderByLineMissedDescPackageNameAsc(Long reportId);

	/**
	 * Per-package coverage totals aggregated in the database — for the package/module
	 * coverage views, which would otherwise load every file row to sum in Java.
	 */
	@Query("""
			select f.packageName as packageName, sum(f.lineCovered) as lineCovered, sum(f.lineMissed) as lineMissed,
			       sum(f.branchCovered) as branchCovered, sum(f.branchMissed) as branchMissed, count(f) as files
			from CoverageFileEntry f
			where f.report.id = :reportId
			group by f.packageName
			""")
	List<PackageCoverage> aggregateByPackage(@Param("reportId") Long reportId);

}
