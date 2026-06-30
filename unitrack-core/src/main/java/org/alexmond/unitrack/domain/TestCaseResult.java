package org.alexmond.unitrack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** A single &lt;testcase&gt; outcome within a run. */
@Entity
@Table(name = "test_case_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TestCaseResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "run_id", nullable = false)
	private TestRun run;

	@Column(name = "suite_name")
	private String suiteName;

	@Column(name = "class_name")
	private String className;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TestStatus status;

	@Column(name = "duration_ms")
	private long durationMs;

	@Column(name = "failure_type")
	private String failureType;

	@Column(name = "failure_message", length = 2000)
	private String failureMessage;

	@Column(name = "failure_stacktrace", length = 100_000)
	private String failureStacktrace;

	@Column(name = "system_out", length = 100_000)
	private String systemOut;

	@Column(name = "system_err", length = 100_000)
	private String systemErr;

	/** Attachment URLs (from {@code [[ATTACHMENT|...]]} markers), newline-separated. */
	@Column(length = 8000)
	private String attachments;

	/**
	 * Explicit build module from the uploader (#393), or null to fall back to package
	 * derivation.
	 */
	@Column(name = "module")
	private String module;

	public TestCaseResult(TestRun run, String suiteName, String className, String name, TestStatus status,
			long durationMs) {
		this.run = run;
		this.suiteName = suiteName;
		this.className = className;
		this.name = name;
		this.status = status;
		this.durationMs = durationMs;
	}

	public void setModule(String module) {
		this.module = module;
	}

	public void setFailure(String failureType, String failureMessage, String failureStacktrace) {
		this.failureType = failureType;
		this.failureMessage = truncate(failureMessage);
		this.failureStacktrace = failureStacktrace;
	}

	public void setOutputs(String systemOut, String systemErr, List<String> attachments) {
		this.systemOut = systemOut;
		this.systemErr = systemErr;
		this.attachments = (attachments == null || attachments.isEmpty()) ? null : String.join("\n", attachments);
	}

	/** Attachment URLs as a list (empty when none). */
	public List<String> attachmentList() {
		return (attachments == null || attachments.isBlank()) ? List.of() : List.of(attachments.split("\n"));
	}

	private static String truncate(String value) {
		if (value == null) {
			return null;
		}
		return (value.length() <= 2000) ? value : value.substring(0, 2000);
	}

}
