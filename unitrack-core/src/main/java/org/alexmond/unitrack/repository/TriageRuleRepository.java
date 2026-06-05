package org.alexmond.unitrack.repository;

import org.alexmond.unitrack.domain.TriageRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TriageRuleRepository extends JpaRepository<TriageRule, Long> {

	List<TriageRule> findByProjectIdOrderByPriorityAscIdAsc(Long projectId);

}
