package org.alexmond.unitrack.web.api;

import org.alexmond.unitrack.domain.CoverageFileEntry;
import org.alexmond.unitrack.domain.CoverageReport;
import org.alexmond.unitrack.domain.PerfRun;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.TestSuiteResult;
import org.alexmond.unitrack.report.QualityGateResult;

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

	record RunJson(Long id, Long projectId, String branch, String flag, String commit, String buildUrl,
			Instant createdAt, int total, int passed, int failed, int errors, int skipped, long durationMs,
			String status, double passRate, Double lineCoveragePct, Double branchCoveragePct, int uploads) {

		public static RunJson of(TestRun r) {
			return new RunJson(r.getId(), r.getProject().getId(), r.getBranch(), r.getFlag(), r.getCommitSha(),
					r.getBuildUrl(), r.getCreatedAt(), r.getTotalTests(), r.getPassed(), r.getFailed(), r.getErrors(),
					r.getSkipped(), r.getDurationMs(), r.getStatus(), r.passRate(), r.getLineCoveragePct(),
					r.getBranchCoveragePct(), r.getUploads());
		}
	}

	record SuiteJson(String name, int tests, int failures, int errors, int skipped, long durationMs) {
		public static SuiteJson of(TestSuiteResult s) {
			return new SuiteJson(s.getName(), s.getTests(), s.getFailures(), s.getErrors(), s.getSkipped(),
					s.getDurationMs());
		}
	}

	record CaseJson(String suite, String className, String name, String status, long durationMs, String failureType,
			String failureMessage, String systemOut, String systemErr, List<String> attachments) {
		public static CaseJson of(TestCaseResult c) {
			return new CaseJson(c.getSuiteName(), c.getClassName(), c.getName(), c.getStatus().name(),
					c.getDurationMs(), c.getFailureType(), c.getFailureMessage(), c.getSystemOut(), c.getSystemErr(),
					c.attachmentList());
		}
	}

	record CoverageJson(double linePct, double branchPct, double instructionPct, double methodPct, int lineCovered,
			int lineMissed, int branchCovered, int branchMissed, List<FileCoverageJson> files) {
		public static CoverageJson of(CoverageReport r, List<CoverageFileEntry> files) {
			return new CoverageJson(r.getLinePct(), r.getBranchPct(), r.getInstructionPct(), r.getMethodPct(),
					r.getLineCovered(), r.getLineMissed(), r.getBranchCovered(), r.getBranchMissed(),
					files.stream().map(FileCoverageJson::of).toList());
		}
	}

	record FileCoverageJson(String path, double linePct, int lineCovered, int lineMissed) {
		public static FileCoverageJson of(CoverageFileEntry f) {
			return new FileCoverageJson(f.getPath(), f.getLinePct(), f.getLineCovered(), f.getLineMissed());
		}
	}

	record RunDetailJson(RunJson run, List<SuiteJson> suites, List<CaseJson> failures, CoverageJson coverage) {
	}

	/**
	 * CI-consumable quality-gate verdict, resolved by project + commit/branch (no
	 * internal run id needed). {@code passed} maps to the {@code unitrack-gate.sh} exit
	 * code.
	 */
	record GateReportJson(String project, String branch, String commit, String flag, Long runId, String status,
			boolean passed, Double coverageDelta, List<QualityGateResult.RuleResult> rules, String runPath) {
		public static GateReportJson of(TestRun run, QualityGateResult gate, Double coverageDelta) {
			return new GateReportJson(run.getProject().getName(), run.getBranch(), run.getCommitSha(), run.getFlag(),
					run.getId(), gate.status(), gate.passed(), coverageDelta, gate.rules(), "/runs/" + run.getId());
		}
	}

	record IngestResultJson(Long runId, Long projectId, String project, int total, int passed, int failed, int errors,
			int skipped, String status, Double lineCoveragePct, int uploads, Long perfRunId, Double perfP95Ms) {
		public static IngestResultJson of(TestRun r, PerfRun perf) {
			Long projectId = (r != null) ? r.getProject().getId() : ((perf != null) ? perf.getProject().getId() : null);
			String project = (r != null) ? r.getProject().getName()
					: ((perf != null) ? perf.getProject().getName() : null);
			return new IngestResultJson((r != null) ? r.getId() : null, projectId, project,
					(r != null) ? r.getTotalTests() : 0, (r != null) ? r.getPassed() : 0,
					(r != null) ? r.getFailed() : 0, (r != null) ? r.getErrors() : 0, (r != null) ? r.getSkipped() : 0,
					(r != null) ? r.getStatus() : null, (r != null) ? r.getLineCoveragePct() : null,
					(r != null) ? r.getUploads() : 0, (perf != null) ? perf.getId() : null,
					(perf != null) ? perf.getP95Ms() : null);
		}
	}

}
