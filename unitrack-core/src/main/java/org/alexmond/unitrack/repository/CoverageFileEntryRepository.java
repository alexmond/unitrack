package org.alexmond.unitrack.repository;

import org.alexmond.unitrack.domain.CoverageFileEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CoverageFileEntryRepository extends JpaRepository<CoverageFileEntry, Long> {

	/**
	 * Bulk-delete a run's coverage file entries (via its report) — for hard run deletion.
	 */
	@Modifying
	@Query("delete from CoverageFileEntry f where f.report.id in "
			+ "(select r.id from CoverageReport r where r.run.id = :runId)")
	void deleteByRunId(@Param("runId") Long runId);

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

	/** True when at least one file of this report carries an explicit module (#393). */
	boolean existsByReportIdAndModuleIsNotNull(Long reportId);

	/**
	 * Per-module coverage totals, by the explicit module the uploader attached (#393).
	 */
	@Query("""
			select f.module as module, sum(f.lineCovered) as lineCovered, sum(f.lineMissed) as lineMissed,
			       sum(f.branchCovered) as branchCovered, sum(f.branchMissed) as branchMissed, count(f) as files
			from CoverageFileEntry f
			where f.report.id = :reportId
			group by f.module
			""")
	List<ModuleCoverageAgg> aggregateByModule(@Param("reportId") Long reportId);

	/** Files of one explicit module (null {@code module} matches the untagged files). */
	@Query("""
			select f from CoverageFileEntry f
			where f.report.id = :reportId
			  and ((:module is not null and f.module = :module) or (:module is null and f.module is null))
			order by f.lineMissed desc, f.packageName asc
			""")
	List<CoverageFileEntry> findByReportIdAndStoredModule(@Param("reportId") Long reportId,
			@Param("module") String module);

	/**
	 * Per-package totals within one explicit module (null {@code module} = untagged
	 * files).
	 */
	@Query("""
			select f.packageName as packageName, sum(f.lineCovered) as lineCovered, sum(f.lineMissed) as lineMissed,
			       sum(f.branchCovered) as branchCovered, sum(f.branchMissed) as branchMissed, count(f) as files
			from CoverageFileEntry f
			where f.report.id = :reportId
			  and ((:module is not null and f.module = :module) or (:module is null and f.module is null))
			group by f.packageName
			""")
	List<PackageCoverage> aggregateByPackageForModule(@Param("reportId") Long reportId, @Param("module") String module);

}
