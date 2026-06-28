package org.alexmond.unitrack.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A record of one ingest upload attempt — what was uploaded, its outcome, and (on
 * failure) why. Recorded out-of-band from the ingest transaction so a failed/rolled-back
 * ingest still leaves a row, giving operators a processing history (#368).
 */
@Entity
@Table(name = "ingest_job")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngestJob {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	@Setter
	private IngestStatus status;

	@Column(name = "project_name", length = 255)
	private String projectName;

	@Column(length = 255)
	private String branch;

	@Column(name = "commit_sha", length = 64)
	private String commit;

	/** What was uploaded: {@code tests}, {@code perf}, or {@code tests+perf}. */
	@Column(length = 32)
	private String kind;

	@Column(name = "size_bytes")
	private long sizeBytes;

	private String uploader;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "finished_at")
	@Setter
	private Instant finishedAt;

	@Column(name = "duration_ms")
	@Setter
	private Long durationMs;

	@Column(name = "failure_reason", length = 2048)
	@Setter
	private String failureReason;

	@Column(name = "run_id")
	@Setter
	private Long runId;

	@Column(name = "perf_run_id")
	@Setter
	private Long perfRunId;

	@Column(name = "project_id")
	@Setter
	private Long projectId;

	public IngestJob(String projectName, String branch, String commit, String kind, long sizeBytes, String uploader) {
		this.status = IngestStatus.PROCESSING;
		this.projectName = projectName;
		this.branch = branch;
		this.commit = commit;
		this.kind = kind;
		this.sizeBytes = sizeBytes;
		this.uploader = uploader;
	}

}
