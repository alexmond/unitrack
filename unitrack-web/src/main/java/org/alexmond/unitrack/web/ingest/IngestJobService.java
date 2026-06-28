package org.alexmond.unitrack.web.ingest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.IngestJob;
import org.alexmond.unitrack.domain.IngestStatus;
import org.alexmond.unitrack.repository.IngestJobRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records ingest attempts as {@link IngestJob}s so operators have a processing history
 * (#368). Each transition runs in its OWN transaction ({@code REQUIRES_NEW}): a failed
 * ingest rolls back its own work, but the FAILED job record (with the reason) still
 * commits.
 */
@Service
@RequiredArgsConstructor
public class IngestJobService {

	private static final int MAX_REASON = 2048;

	private final IngestJobRepository jobs;

	/** Opens a job in PROCESSING; returns its id to settle later. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Long start(String project, String branch, String commit, String kind, long sizeBytes, String uploader) {
		return this.jobs.save(new IngestJob(project, branch, commit, kind, sizeBytes, uploader)).getId();
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void succeeded(Long jobId, Long projectId, Long runId, Long perfRunId) {
		this.jobs.findById(jobId).ifPresent((job) -> {
			job.setStatus(IngestStatus.PROCESSED);
			job.setProjectId(projectId);
			job.setRunId(runId);
			job.setPerfRunId(perfRunId);
			finish(job);
		});
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void failed(Long jobId, String reason) {
		this.jobs.findById(jobId).ifPresent((job) -> {
			job.setStatus(IngestStatus.FAILED);
			job.setFailureReason(truncate(reason));
			finish(job);
		});
	}

	@Transactional(readOnly = true)
	public List<IngestJob> recent(int limit) {
		return this.jobs.findByOrderByCreatedAtDesc(PageRequest.of(0, limit));
	}

	private static void finish(IngestJob job) {
		Instant now = Instant.now();
		job.setFinishedAt(now);
		job.setDurationMs(Duration.between(job.getCreatedAt(), now).toMillis());
	}

	private static String truncate(String reason) {
		if (reason == null) {
			return "(no message)";
		}
		return (reason.length() <= MAX_REASON) ? reason : reason.substring(0, MAX_REASON);
	}

}
