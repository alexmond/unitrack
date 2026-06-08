package org.alexmond.unitrack.repository;

import java.util.List;

import org.alexmond.unitrack.domain.PerfTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerfTransactionRepository extends JpaRepository<PerfTransaction, Long> {

	List<PerfTransaction> findByPerfRunIdOrderByMeanMsDesc(Long perfRunId);

}
