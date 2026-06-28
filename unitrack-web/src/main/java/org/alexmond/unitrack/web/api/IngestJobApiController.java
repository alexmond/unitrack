package org.alexmond.unitrack.web.api;

import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.IngestJob;
import org.alexmond.unitrack.web.account.ProjectAccessService;
import org.alexmond.unitrack.web.ingest.IngestJobService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only API over the ingest processing history (#368). The list ({@code GET
 * /api/v1/ingest-jobs}) is admin-only (it can name private projects); a single job
 * ({@code GET /api/v1/ingest-jobs/{id}}) is readable by an admin or the job's own
 * uploader, so CI can poll the job it just enqueued. Each row carries the outcome and, on
 * failure, the reason.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class IngestJobApiController {

	private static final int DEFAULT_LIMIT = 100;

	private static final int MAX_LIMIT = 500;

	private final IngestJobService ingestJobs;

	private final ProjectAccessService access;

	@GetMapping("/ingest-jobs")
	public List<IngestJobJson> list(@RequestParam(required = false) Integer limit) {
		int max = Math.min((limit != null && limit > 0) ? limit : DEFAULT_LIMIT, MAX_LIMIT);
		return this.ingestJobs.recent(max).stream().map(IngestJobJson::of).toList();
	}

	@GetMapping("/ingest-jobs/{id}")
	public IngestJobJson get(@PathVariable Long id) {
		IngestJob job = this.ingestJobs.find(id)
			.filter(this::canSee)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such ingest job"));
		return IngestJobJson.of(job);
	}

	/**
	 * Admin sees any job; otherwise only the uploader sees their own (don't leak
	 * others').
	 */
	private boolean canSee(IngestJob job) {
		if (isAdmin()) {
			return true;
		}
		String me = this.access.currentUsername();
		return me != null && me.equals(job.getUploader());
	}

	private static boolean isAdmin() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return auth != null && auth.getAuthorities().stream().anyMatch((a) -> "ROLE_ADMIN".equals(a.getAuthority()));
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
