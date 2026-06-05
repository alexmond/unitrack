package org.alexmond.unitrack.web.api;

import org.alexmond.unitrack.domain.CoverageFileEntry;
import org.alexmond.unitrack.domain.CoverageReport;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.TestSuiteResult;

import java.time.Instant;
import java.util.List;

/**
 * JSON DTOs for the REST API. Kept separate from JPA entities so the wire format is
 * stable. A holder interface so the nested records are implicitly static.
 */
public interface ApiResponses {

	record ProjectJson(Long id, String name, String repoUrl, long runCount) {
		public static ProjectJson of(Project p, long runCount) {
			return new ProjectJson(p.getId(), p.getName(), p.getRepoUrl(), runCount);
		}
	}

	public record RunJson(Long id, Long projectId, String branch, String commit, String buildUrl, Instant createdAt,
			int total, int passed, int failed, int errors, int skipped, long durationMs, String status, double passRate,
			Double lineCoveragePct, Double branchCoveragePct) {

		public static RunJson of(TestRun r) {
			return new RunJson(r.getId(), r.getProject().getId(), r.getBranch(), r.getCommitSha(), r.getBuildUrl(),
					r.getCreatedAt(), r.getTotalTests(), r.getPassed(), r.getFailed(), r.getErrors(), r.getSkipped(),
					r.getDurationMs(), r.getStatus(), r.passRate(), r.getLineCoveragePct(), r.getBranchCoveragePct());
		}
	}

	public record SuiteJson(String name, int tests, int failures, int errors, int skipped, long durationMs) {
		public static SuiteJson of(TestSuiteResult s) {
			return new SuiteJson(s.getName(), s.getTests(), s.getFailures(), s.getErrors(), s.getSkipped(),
					s.getDurationMs());
		}
	}

	public record CaseJson(String suite, String className, String name, String status, long durationMs,
			String failureType, String failureMessage) {
		public static CaseJson of(TestCaseResult c) {
			return new CaseJson(c.getSuiteName(), c.getClassName(), c.getName(), c.getStatus().name(),
					c.getDurationMs(), c.getFailureType(), c.getFailureMessage());
		}
	}

	public record CoverageJson(double linePct, double branchPct, double instructionPct, double methodPct,
			int lineCovered, int lineMissed, int branchCovered, int branchMissed, List<FileCoverageJson> files) {
		public static CoverageJson of(CoverageReport r, List<CoverageFileEntry> files) {
			return new CoverageJson(r.getLinePct(), r.getBranchPct(), r.getInstructionPct(), r.getMethodPct(),
					r.getLineCovered(), r.getLineMissed(), r.getBranchCovered(), r.getBranchMissed(),
					files.stream().map(FileCoverageJson::of).toList());
		}
	}

	public record FileCoverageJson(String path, double linePct, int lineCovered, int lineMissed) {
		public static FileCoverageJson of(CoverageFileEntry f) {
			return new FileCoverageJson(f.getPath(), f.getLinePct(), f.getLineCovered(), f.getLineMissed());
		}
	}

	public record RunDetailJson(RunJson run, List<SuiteJson> suites, List<CaseJson> failures, CoverageJson coverage) {
	}

	public record IngestResultJson(Long runId, Long projectId, String project, int total, int passed, int failed,
			int errors, int skipped, String status, Double lineCoveragePct) {
		public static IngestResultJson of(TestRun r) {
			return new IngestResultJson(r.getId(), r.getProject().getId(), r.getProject().getName(), r.getTotalTests(),
					r.getPassed(), r.getFailed(), r.getErrors(), r.getSkipped(), r.getStatus(), r.getLineCoveragePct());
		}
	}

}
