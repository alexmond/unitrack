package org.alexmond.unitrack.repository;

import org.alexmond.unitrack.domain.ShareLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {

	/** Bulk-delete a run's share links — for hard run deletion. */
	@Modifying
	@Query("delete from ShareLink l where l.run.id = :runId")
	void deleteByRunId(@Param("runId") Long runId);

	Optional<ShareLink> findByTokenHash(String tokenHash);

	List<ShareLink> findByRunIdAndRevokedFalseOrderByCreatedAtDesc(Long runId);

}
