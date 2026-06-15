package org.alexmond.unitrack.repository;

import org.alexmond.unitrack.domain.ShareLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {

	Optional<ShareLink> findByTokenHash(String tokenHash);

	List<ShareLink> findByRunIdAndRevokedFalseOrderByCreatedAtDesc(Long runId);

}
