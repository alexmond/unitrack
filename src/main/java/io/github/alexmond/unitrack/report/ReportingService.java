package io.github.alexmond.unitrack.report;

import io.github.alexmond.unitrack.domain.CoverageFileEntry;
import io.github.alexmond.unitrack.domain.CoverageReport;
import io.github.alexmond.unitrack.domain.Project;
import io.github.alexmond.unitrack.domain.TestCaseResult;
import io.github.alexmond.unitrack.domain.TestRun;
import io.github.alexmond.unitrack.domain.TestStatus;
import io.github.alexmond.unitrack.domain.TestSuiteResult;
import io.github.alexmond.unitrack.repository.CoverageFileEntryRepository;
import io.github.alexmond.unitrack.repository.CoverageReportRepository;
import io.github.alexmond.unitrack.repository.ProjectRepository;
import io.github.alexmond.unitrack.repository.TestCaseResultRepository;
import io.github.alexmond.unitrack.repository.TestRunRepository;
import io.github.alexmond.unitrack.repository.TestSuiteResultRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Read-side queries shared by the REST API and the web dashboard. */
@Service
@Transactional(readOnly = true)
public class ReportingService {

    private final ProjectRepository projects;
    private final TestRunRepository runs;
    private final TestSuiteResultRepository suites;
    private final TestCaseResultRepository cases;
    private final CoverageReportRepository coverageReports;
    private final CoverageFileEntryRepository coverageFiles;

    public ReportingService(ProjectRepository projects, TestRunRepository runs,
                            TestSuiteResultRepository suites, TestCaseResultRepository cases,
                            CoverageReportRepository coverageReports, CoverageFileEntryRepository coverageFiles) {
        this.projects = projects;
        this.runs = runs;
        this.suites = suites;
        this.cases = cases;
        this.coverageReports = coverageReports;
        this.coverageFiles = coverageFiles;
    }

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

    public List<TestSuiteResult> suitesFor(Long runId) {
        return suites.findByRunIdOrderByNameAsc(runId);
    }

    public List<TestCaseResult> failedCasesFor(Long runId) {
        return cases.findByRunIdAndStatusInOrderByClassNameAscNameAsc(
                runId, List.of(TestStatus.FAILED, TestStatus.ERROR));
    }

    public List<TestCaseResult> allCasesFor(Long runId) {
        return cases.findByRunIdOrderByStatusAscClassNameAscNameAsc(runId);
    }

    public Optional<CoverageReport> coverageFor(Long runId) {
        return coverageReports.findByRunId(runId);
    }

    public List<CoverageFileEntry> coverageFiles(Long reportId, int limit) {
        List<CoverageFileEntry> all = coverageFiles.findByReportIdOrderByLineMissedDescPackageNameAsc(reportId);
        return all.size() > limit ? all.subList(0, limit) : all;
    }
}
