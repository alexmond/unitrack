package org.alexmond.unitrack.report;

import org.alexmond.unitrack.domain.CoverageFileEntry;
import org.alexmond.unitrack.domain.CoverageReport;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.TestStatus;
import org.alexmond.unitrack.domain.TestSuiteResult;
import org.alexmond.unitrack.repository.BrokenSince;
import org.alexmond.unitrack.repository.CoverageFileEntryRepository;
import org.alexmond.unitrack.repository.CoverageReportRepository;
import org.alexmond.unitrack.repository.PackageCoverage;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.alexmond.unitrack.repository.TestCaseResultRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.alexmond.unitrack.repository.TestSuiteResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

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

	private final org.alexmond.unitrack.repository.PerfRunRepository perfRuns;

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

	/**
	 * Most recent runs first, optionally scoped to one branch (null/blank = all
	 * branches).
	 */
	public List<TestRun> recentRuns(Long projectId, String branch, int limit) {
		if (branch == null || branch.isBlank()) {
			return recentRuns(projectId, limit);
		}
		return runs.findByProjectIdAndBranchOrderByCreatedAtDesc(projectId, branch, PageRequest.ofSize(limit));
	}

	/**
	 * The latest two runs of every project, grouped by project id (newest first) — the
	 * board's batch fetch, so it doesn't query runs once per project.
	 */
	public Map<Long, List<TestRun>> latestRunsByProject() {
		Map<Long, List<TestRun>> byProject = new HashMap<>();
		for (TestRun r : runs.findLatestTwoRunsPerProject()) {
			byProject.computeIfAbsent(r.getProject().getId(), (k) -> new ArrayList<>()).add(r);
		}
		byProject.values().forEach((list) -> list.sort(Comparator.comparing(TestRun::getCreatedAt).reversed()));
		return byProject;
	}

	/**
	 * Per-project "broken since" (regression age) for the board, keyed by project id —
	 * one set-based query, only projects whose latest run is failing are present.
	 */
	public Map<Long, BrokenSince> brokenSinceByProject() {
		Map<Long, BrokenSince> byProject = new HashMap<>();
		for (BrokenSince b : runs.findBrokenSince()) {
			byProject.put(b.getProjectId(), b);
		}
		return byProject;
	}

	/** Oldest first — for trend charts. */
	public List<TestRun> trendRuns(Long projectId, int limit) {
		List<TestRun> recent = runs.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.ofSize(limit));
		return recent.reversed();
	}

	/** Oldest first, optionally scoped to one branch (null/blank = all branches). */
	public List<TestRun> trendRuns(Long projectId, String branch, int limit) {
		return recentRuns(projectId, branch, limit).reversed();
	}

	/**
	 * Trend (oldest first) for one flag — so a multi-flag project (split-by-module: a
	 * rollup plus per-module flags) charts a single coherent series instead of
	 * interleaving flags. Branch is optional (null/blank = all branches).
	 */
	public List<TestRun> trendRuns(Long projectId, String branch, String flag, int limit) {
		PageRequest page = PageRequest.ofSize(limit);
		List<TestRun> recent = (branch == null || branch.isBlank())
				? runs.findByProjectIdAndFlagOrderByCreatedAtDesc(projectId, flag, page)
				: runs.findByProjectIdAndBranchAndFlagOrderByCreatedAtDesc(projectId, branch, flag, page);
		return recent.reversed();
	}

	public Optional<TestRun> findRun(Long id) {
		return runs.findById(id);
	}

	/** Recent perf runs (newest first) for a project. */
	public List<org.alexmond.unitrack.domain.PerfRun> recentPerfRuns(Long projectId, int limit) {
		return perfRuns.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.ofSize(limit));
	}

	public Optional<org.alexmond.unitrack.domain.PerfRun> findPerfRun(Long id) {
		return perfRuns.findById(id);
	}

	/** Performance trend for a project, oldest run first (for charting). */
	public List<PerfTrendPoint> perfTrend(Long projectId, int limit) {
		return perfTrend(projectId, null, limit);
	}

	/**
	 * Performance trend scoped to one flag, oldest run first. {@code PerfRun} carries a
	 * flag too, so a split-by-module project would interleave per-module load runs into a
	 * sawtooth (same trap as the suite-time/coverage trends). Pass the rollup flag;
	 * null/blank charts every run.
	 */
	public List<PerfTrendPoint> perfTrend(Long projectId, String flag, int limit) {
		PageRequest page = PageRequest.ofSize(limit);
		List<org.alexmond.unitrack.domain.PerfRun> recent = (flag == null || flag.isBlank())
				? perfRuns.findByProjectIdOrderByCreatedAtDesc(projectId, page)
				: perfRuns.findByProjectIdAndFlagOrderByCreatedAtDesc(projectId, flag, page);
		return recent.reversed().stream().map(PerfTrendPoint::of).toList();
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

	/**
	 * Worst-covered files within one module (null/blank = all). The module is derived the
	 * same way {@link #moduleCoverage} derives it — the package segment after the longest
	 * common prefix — so a module drill-down from the overview lines up exactly.
	 */
	public List<CoverageFileEntry> coverageFiles(Long reportId, String module, int limit) {
		if (module == null || module.isBlank()) {
			return coverageFiles(reportId, limit);
		}
		List<CoverageFileEntry> inModule = filterByModule(
				coverageFiles.findByReportIdOrderByLineMissedDescPackageNameAsc(reportId), module,
				CoverageFileEntry::getPackageName);
		return (inModule.size() > limit) ? inModule.subList(0, limit) : inModule;
	}

	/** Per-package coverage within one module (null/blank = all packages). */
	public List<CoveragePackage> coveragePackages(Long reportId, String module) {
		return filterByModule(coveragePackages(reportId), module, CoveragePackage::packageName);
	}

	/**
	 * Keep only the items whose package falls in {@code module}, deriving each item's
	 * module exactly as {@link #moduleCoverage} does (segment after the common prefix;
	 * blank / {@code (default)} packages are the {@code (root)} module).
	 */
	private static <T> List<T> filterByModule(List<T> items, String module, Function<T, String> packageOf) {
		if (module == null || module.isBlank() || items.isEmpty()) {
			return items;
		}
		List<String[]> segments = items.stream()
			.map((i) -> splitPackage("(default)".equals(packageOf.apply(i)) ? null : packageOf.apply(i)))
			.toList();
		int prefix = commonPrefixLength(segments);
		List<T> out = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			String[] s = segments.get(i);
			String mod = (s.length > prefix) ? s[prefix] : "(root)";
			if (mod.equals(module)) {
				out.add(items.get(i));
			}
		}
		return out;
	}

	/**
	 * The most recent coverage report for a project (latest run that carried coverage).
	 */
	public Optional<CoverageReport> latestCoverage(Long projectId) {
		return coverageReports.findLatestForProject(projectId, PageRequest.ofSize(1)).stream().findFirst();
	}

	/** Per-package line/branch totals for a coverage report, sorted by package name. */
	public List<CoveragePackage> coveragePackages(Long reportId) {
		Map<String, long[]> byPackage = new TreeMap<>();
		for (PackageCoverage p : coverageFiles.aggregateByPackage(reportId)) {
			String pkg = (p.getPackageName() == null || p.getPackageName().isBlank()) ? "(default)"
					: p.getPackageName();
			byPackage.merge(pkg, sums(p), ReportingService::addSums);
		}
		return byPackage.entrySet()
			.stream()
			.map((e) -> new CoveragePackage(e.getKey(), (int) e.getValue()[0], (int) e.getValue()[1],
					(int) e.getValue()[2], (int) e.getValue()[3]))
			.toList();
	}

	/**
	 * Per-module line/branch totals for a coverage report. A multi-module project is
	 * uploaded flat (no module concept), so the module is derived from the package tree:
	 * the segment that follows the longest package prefix common to every package (e.g.
	 * {@code …builder/<module>/…}). Returns at most one entry for a single-module
	 * project, so callers can hide the view. Aggregated per package in SQL, not per file.
	 */
	public List<ModuleCoverage> moduleCoverage(Long reportId) {
		List<PackageCoverage> packages = coverageFiles.aggregateByPackage(reportId);
		if (packages.isEmpty()) {
			return List.of();
		}
		List<String[]> segments = packages.stream().map((p) -> splitPackage(p.getPackageName())).toList();
		int prefix = commonPrefixLength(segments);
		Map<String, long[]> byModule = new TreeMap<>();
		for (int i = 0; i < packages.size(); i++) {
			PackageCoverage p = packages.get(i);
			String[] s = segments.get(i);
			String module = (s.length > prefix) ? s[prefix] : "(root)";
			long[] a = byModule.computeIfAbsent(module, (k) -> new long[5]);
			a[0] += p.getLineCovered();
			a[1] += p.getLineMissed();
			a[2] += p.getBranchCovered();
			a[3] += p.getBranchMissed();
			a[4] += p.getFiles();
		}
		return byModule.entrySet()
			.stream()
			.map((e) -> new ModuleCoverage(e.getKey(), (int) e.getValue()[0], (int) e.getValue()[1],
					(int) e.getValue()[2], (int) e.getValue()[3], (int) e.getValue()[4]))
			.toList();
	}

	private static long[] sums(PackageCoverage p) {
		return new long[] { p.getLineCovered(), p.getLineMissed(), p.getBranchCovered(), p.getBranchMissed() };
	}

	private static long[] addSums(long[] a, long[] b) {
		for (int i = 0; i < a.length; i++) {
			a[i] += b[i];
		}
		return a;
	}

	private static String[] splitPackage(String pkg) {
		return (pkg == null || pkg.isBlank()) ? new String[0] : pkg.split("[./]+");
	}

	/** The number of leading package segments shared by every file. */
	private static int commonPrefixLength(List<String[]> segments) {
		int len = Integer.MAX_VALUE;
		for (String[] s : segments) {
			len = Math.min(len, s.length);
		}
		String[] first = segments.get(0);
		for (int i = 0; i < len; i++) {
			for (String[] s : segments) {
				if (!first[i].equals(s[i])) {
					return i;
				}
			}
		}
		return len;
	}

}
