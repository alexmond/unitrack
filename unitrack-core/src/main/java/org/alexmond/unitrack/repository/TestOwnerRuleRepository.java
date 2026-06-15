package org.alexmond.unitrack.repository;

import org.alexmond.unitrack.domain.TestOwnerRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestOwnerRuleRepository extends JpaRepository<TestOwnerRule, Long> {

	List<TestOwnerRule> findByProjectIdOrderByPriorityAscIdAsc(Long projectId);

}
