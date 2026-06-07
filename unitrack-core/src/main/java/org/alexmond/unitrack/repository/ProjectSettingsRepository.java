package org.alexmond.unitrack.repository;

import java.util.Optional;

import org.alexmond.unitrack.domain.ProjectSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectSettingsRepository extends JpaRepository<ProjectSettings, Long> {

	Optional<ProjectSettings> findByProjectId(Long projectId);

}
