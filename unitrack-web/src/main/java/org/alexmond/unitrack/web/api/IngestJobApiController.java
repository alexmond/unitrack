package org.alexmond.unitrack.web.api;

import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.IngestJob;
import org.alexmond.unitrack.web.ingest.IngestJobService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only API over the ingest processing history (#368). Admin-only (enforced in
 * {@code SecurityConfig}; {@code /api/v1/ingest-jobs} requires {@code ROLE_ADMIN}).
 * Newest first; each row carries the outcome and, on failure, the reason.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class IngestJobApiController {

	private static final int DEFAULT_LIMIT = 100;

	private static final int MAX_LIMIT = 500;

	private final IngestJobService ingestJobs;

	@GetMapping("/ingest-jobs")
	public List<IngestJobJson> list(@RequestParam(required = false) Integer limit) {
		int max = Math.min((limit != null && limit > 0) ? limit : DEFAULT_LIMIT, MAX_LIMIT);
		return this.ingestJobs.recent(max).stream().map(IngestJobJson::of).toList();
	}

	/** One ingest attempt as JSON. */
	public record IngestJobJson(Long id, String status, String project, String branch, String commit, String kind,
			long sizeBytes, Instant createdAt, Instant finishedAt, Long durationMs, String failureReason, Long runId,
			Long perfRunId) {

		static IngestJobJson of(IngestJob job) {
			return new IngestJobJson(job.getId(), job.getStatus().name(), job.getProjectName(), job.getBranch(),
					job.getCommit(), job.getKind(), job.getSizeBytes(), job.getCreatedAt(), job.getFinishedAt(),
					job.getDurationMs(), job.getFailureReason(), job.getRunId(), job.getPerfRunId());
		}

	}

}
