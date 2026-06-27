package org.alexmond.unitrack.repository;

import java.util.List;
import java.util.Optional;

import org.alexmond.unitrack.domain.ProjectMembership;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, Long> {

	List<ProjectMembership> findByProjectIdOrderByRoleAscIdAsc(Long projectId);

	Optional<ProjectMembership> findByProjectIdAndUserId(Long projectId, Long userId);

	List<ProjectMembership> findByUserId(Long userId);

}
