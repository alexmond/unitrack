package org.alexmond.unitrack.web.webhook;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.report.BranchCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily job that hard-deletes branches whose latest run is past the configured retention
 * ({@code unitrack.branches.retain-days}). A no-op when retention is disabled (0).
 */
@Component
@RequiredArgsConstructor
public class BranchExpiryScheduler {

	private static final Logger log = LoggerFactory.getLogger(BranchExpiryScheduler.class);

	private final BranchCleanupService cleanup;

	/** Runs daily (override with {@code unitrack.branches.expiry-cron}). */
	@Scheduled(cron = "${unitrack.branches.expiry-cron:0 30 3 * * *}")
	public void run() {
		int removed = this.cleanup.expireStaleBranches(Instant.now());
		if (removed > 0) {
			log.info("Branch expiry removed {} stale run(s)", removed);
		}
	}

}
