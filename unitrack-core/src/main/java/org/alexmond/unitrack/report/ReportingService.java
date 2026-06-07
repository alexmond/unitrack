package org.alexmond.unitrack.report;

import org.alexmond.unitrack.domain.CoverageFileEntry;
import org.alexmond.unitrack.domain.CoverageReport;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.TestStatus;
import org.alexmond.unitrack.domain.TestSuiteResult;
import org.alexmond.unitrack.repository.CoverageFileEntryRepository;
import org.alexmond.unitrack.repository.CoverageReportRepository;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.alexmond.unitrack.repository.TestCaseResultRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.alexmond.unitrack.repository.TestSuiteResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-side queries shared by the REST API and the web dashboard. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportingService {

	private final ProjectRepository projects;

	private final TestRunRepository runs;

	private final TestSuiteResultRepository suites;

	private final TestCaseResultRepository cases;

	private final CoverageReportRepository coverageReports;

	private final CoverageFileEntryRepository coverageFiles;

	public List<Project> listProjects() {
		return projects.findAllByOrderByNameAsc();
	}

	public Optional<Project> findProject(Long id) {
		return projects.findById(id);
	}

	public long runCount(Long projectId) {
		return runs.countByProjectId(projectId);
	}

	/** Most recent runs first — for run lists. */
	public List<TestRun> recentRuns(Long projectId, int limit) {
		return runs.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.ofSize(limit));
	}

	/** Oldest first — for trend charts. */
	public List<TestRun> trendRuns(Long projectId, int limit) {
		List<TestRun> recent = runs.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.ofSize(limit));
		return recent.reversed();
	}

	public Optional<TestRun> findRun(Long id) {
		return runs.findById(id);
	}

	public Optional<Project> findProjectByName(String name) {
		return projects.findByName(name);
	}

	/** Latest run for a project + commit (optional flag) — for CI gate lookups by SHA. */
	public Optional<TestRun> latestRunByCommit(Long projectId, String commit, String flag) {
		return runs.findLatestByCommit(projectId, commit, flag, PageRequest.ofSize(1)).stream().findFirst();
	}

	/**
	 * Latest run for a project + branch (optional flag) — for CI gate lookups by branch.
	 */
	public Optional<TestRun> latestRunByBranch(Long projectId, String branch, String flag) {
		return runs.findLatestByBranch(projectId, branch, flag, PageRequest.ofSize(1)).stream().findFirst();
	}

	/** Latest coverage/status per coverage flag (component) for a project. */
	public List<FlagSummary> flagSummaries(Long projectId) {
		return runs.findDistinctFlags(projectId)
			.stream()
			.map((flag) -> runs.findFirstByProjectIdAndFlagOrderByCreatedAtDesc(projectId, flag).orElse(null))
			.filter(Objects::nonNull)
			.map((r) -> new FlagSummary(r.getFlag(), r.getLineCoveragePct(), r.getId(), r.getCreatedAt(),
					r.getStatus()))
			.toList();
	}

	public List<TestSuiteResult> suitesFor(Long runId) {
		return suites.findByRunIdOrderByNameAsc(runId);
	}

	public List<TestCaseResult> failedCasesFor(Long runId) {
		return cases.findByRunIdAndStatusInOrderByClassNameAscNameAsc(runId,
				List.of(TestStatus.FAILED, TestStatus.ERROR));
	}

	public List<TestCaseResult> allCasesFor(Long runId) {
		return cases.findByRunIdOrderByStatusAscClassNameAscNameAsc(runId);
	}

	public Optional<CoverageReport> coverageFor(Long runId) {
		return coverageReports.findByRunId(runId);
	}

	public List<CoverageFileEntry> coverageFiles(Long reportId, int limit) {
		List<CoverageFileEntry> all = coverageFiles.findByReportIdOrderByLineMissedDescPackageNameAsc(reportId);
		return (all.size() > limit) ? all.subList(0, limit) : all;
	}

}
