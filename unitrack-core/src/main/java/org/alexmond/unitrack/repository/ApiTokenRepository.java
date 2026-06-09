package org.alexmond.unitrack.repository;

import org.alexmond.unitrack.domain.ApiToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {

	Optional<ApiToken> findByTokenHash(String tokenHash);

	List<ApiToken> findByUserIdOrderByCreatedAtDesc(Long userId);

	/** Live tokens expiring within the window that have not yet had a reminder sent. */
	List<ApiToken> findByRevokedFalseAndExpiryRemindedAtIsNullAndExpiresAtBetween(Instant from, Instant to);

}
