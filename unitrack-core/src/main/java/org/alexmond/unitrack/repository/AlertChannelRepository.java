package org.alexmond.unitrack.repository;

import org.alexmond.unitrack.domain.AlertChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertChannelRepository extends JpaRepository<AlertChannel, Long> {

	List<AlertChannel> findByProjectIdOrderByCreatedAtDesc(Long projectId);

	List<AlertChannel> findByProjectIdAndEnabledTrueOrderByCreatedAtDesc(Long projectId);

}
