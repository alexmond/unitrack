package org.alexmond.unitrack.repository;

import org.alexmond.unitrack.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByName(String name);

    List<Project> findAllByOrderByNameAsc();
}
