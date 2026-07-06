package org.alexmond.unitrack.repository;

import java.util.List;

import org.alexmond.unitrack.domain.PerfTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerfTransactionRepository extends JpaRepository<PerfTransaction, Long> {

	List<PerfTransaction> findByPerfRunIdOrderByMeanMsDesc(Long perfRunId);

	/**
	 * One transaction's rows across a project's runs of the same flag, oldest first — the
	 * per-label history behind the transaction detail page (latency over runs). Fetches
	 * the owning run so the label/time/commit are available after the session closes.
	 */
	@Query("select t from PerfTransaction t join fetch t.perfRun r "
			+ "where r.project.id = :projectId and t.label = :label and r.flag = :flag order by r.createdAt")
	List<PerfTransaction> findSeries(@Param("projectId") Long projectId, @Param("label") String label,
			@Param("flag") String flag);

}
