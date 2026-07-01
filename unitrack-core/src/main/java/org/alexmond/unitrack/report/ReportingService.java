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

	/**
	 * Id of the run just before/after this one in its project+branch+flag series, or
	 * null.
	 */
	public Long previousRunId(TestRun run) {
		return runs
			.findPrevious(run.getProject().getId(), run.getBranch(), run.getFlag(), run.getCreatedAt(),
					PageRequest.ofSize(1))
			.stream()
			.findFirst()
			.map(TestRun::getId)
			.orElse(null);
	}

	public Long nextRunId(TestRun run) {
		return runs
			.findNext(run.getProject().getId(), run.getBranch(), run.getFlag(), run.getCreatedAt(),
					PageRequest.ofSize(1))
			.stream()
			.findFirst()
			.map(TestRun::getId)
			.orElse(null);
	}

	public Long previousPerfRunId(org.alexmond.unitrack.domain.PerfRun run) {
		return perfRuns
			.findPrevious(run.getProject().getId(), run.getBranch(), run.getFlag(), run.getCreatedAt(),
					PageRequest.ofSize(1))
			.stream()
			.findFirst()
			.map(org.alexmond.unitrack.domain.PerfRun::getId)
			.orElse(null);
	}

	public Long nextPerfRunId(org.alexmond.unitrack.domain.PerfRun run) {
		return perfRuns
			.findNext(run.getProject().getId(), run.getBranch(), run.getFlag(), run.getCreatedAt(),
					PageRequest.ofSize(1))
			.stream()
			.findFirst()
			.map(org.alexmond.unitrack.domain.PerfRun::getId)
			.orElse(null);
	}

	/** Recent perf runs (newest first) for a project. */
	public List<org.alexmond.unitrack.domain.PerfRun> recentPerfRuns(Long projectId, int limit) {
		return perfRuns.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.ofSize(limit));
	}

	/** Recent perf runs (newest first), optionally scoped to one flag (series). */
	public List<org.alexmond.unitrack.domain.PerfRun> recentPerfRuns(Long projectId, String flag, int limit) {
		PageRequest page = PageRequest.ofSize(limit);
		return (flag == null || flag.isBlank()) ? perfRuns.findByProjectIdOrderByCreatedAtDesc(projectId, page)
				: perfRuns.findByProjectIdAndFlagOrderByCreatedAtDesc(projectId, flag, page);
	}

	/** Distinct perf flags (series) for a project — for the perf flag filter. */
	public List<String> perfFlags(Long projectId) {
		return perfRuns.findDistinctFlagsByProjectId(projectId);
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

	/**
	 * Distinct flags (series) a project has test runs for — for the Tests-page flag
	 * filter.
	 */
	public List<String> testFlags(Long projectId) {
		return runs.findDistinctFlags(projectId);
	}

	/**
	 * Distinct branch names (alphabetical) a project has runs for — for the analytics
	 * scope dropdown. One query, unlike {@link BranchService#list} which also computes
	 * each branch's latest-run status/coverage/count (a per-branch N+1); use that only
	 * when the summary columns are actually shown (the Overview branches list).
	 */
	public List<String> branchNames(Long projectId) {
		return runs.findDistinctBranches(projectId);
	}

	/**
	 * Per-module test totals for a run, for the Tests page. The module is the explicit
	 * one the uploader sent (#393) when present; otherwise — since Surefire/JUnit XML
	 * carries no module — it's derived from each test's package exactly as
	 * {@link #moduleCoverage} derives coverage modules (segment after the longest common
	 * package prefix), so the two breakdowns line up. Returns empty for a single-module
	 * project so the caller can hide the section.
	 */
	public List<TestModuleRow> testModules(Long runId) {
		List<TestCaseResult> all = cases.findByRunIdOrderByStatusAscClassNameAscNameAsc(runId);
		if (all.isEmpty()) {
			return List.of();
		}
		List<String> modules = moduleOfEach(all);
		Map<String, int[]> byModule = new TreeMap<>();
		for (int i = 0; i < all.size(); i++) {
			int[] a = byModule.computeIfAbsent(modules.get(i), (k) -> new int[3]);
			a[0]++;
			TestStatus st = all.get(i).getStatus();
			if (st == TestStatus.PASSED) {
				a[1]++;
			}
			else if (st == TestStatus.SKIPPED) {
				a[2]++;
			}
		}
		if (byModule.size() <= 1) {
			return List.of();
		}
		return byModule.entrySet()
			.stream()
			.map((e) -> new TestModuleRow(e.getKey(), e.getValue()[0], e.getValue()[1],
					e.getValue()[0] - e.getValue()[1] - e.getValue()[2], e.getValue()[2]))
			.toList();
	}

	/**
	 * Per-module suite-time totals for a run, for the Test timing page. Same module
	 * resolution as {@link #testModules} (explicit uploader module, else
	 * package-derived); empty for a single-module project so the caller can hide the
	 * section.
	 */
	public List<TestModuleTiming> testModuleTiming(Long runId) {
		List<TestCaseResult> all = cases.findByRunIdOrderByStatusAscClassNameAscNameAsc(runId);
		if (all.isEmpty()) {
			return List.of();
		}
		List<String> modules = moduleOfEach(all);
		Map<String, long[]> byModule = new TreeMap<>();
		for (int i = 0; i < all.size(); i++) {
			long[] a = byModule.computeIfAbsent(modules.get(i), (k) -> new long[2]);
			a[0]++;
			a[1] += all.get(i).getDurationMs();
		}
		if (byModule.size() <= 1) {
			return List.of();
		}
		return byModule.entrySet()
			.stream()
			.map((e) -> new TestModuleTiming(e.getKey(), (int) e.getValue()[0], e.getValue()[1]))
			.toList();
	}

	/**
	 * The module of each test case, in order: the explicit uploader-supplied module
	 * (#393) when any case carries one, otherwise the package-derived module (segment
	 * after the longest common package prefix).
	 */
	private static List<String> moduleOfEach(List<TestCaseResult> cases) {
		boolean hasExplicit = cases.stream().anyMatch((c) -> c.getModule() != null && !c.getModule().isBlank());
		if (hasExplicit) {
			return cases.stream()
				.map((c) -> (c.getModule() != null && !c.getModule().isBlank()) ? c.getModule() : "(none)")
				.toList();
		}
		List<String[]> segments = cases.stream().map((c) -> splitPackage(packageOf(c.getClassName()))).toList();
		int prefix = commonPrefixLength(segments);
		return segments.stream().map((s) -> (s.length > prefix) ? s[prefix] : "(root)").toList();
	}

	/**
	 * The package of a fully-qualified class name (everything before the last dot), or
	 * null.
	 */
	private static String packageOf(String className) {
		if (className == null) {
			return null;
		}
		int dot = className.lastIndexOf('.');
		return (dot > 0) ? className.substring(0, dot) : null;
	}

	public List<TestCaseResult> allCasesFor(Long runId) {
		return cases.findByRunIdOrderByStatusAscClassNameAscNameAsc(runId);
	}

	/**
	 * The module label of each case in {@code cases} (explicit uploader module, else
	 * package-derived) in input order — exposed for module-scoped Tests views so a caller
	 * can filter the roster and KPI tiles to one module without re-deriving the prefix.
	 */
	public List<String> moduleOf(List<TestCaseResult> cases) {
		return moduleOfEach(cases);
	}

	/**
	 * Per-run {@code [passed, failed+errors]} counts for a single module across the given
	 * runs (kept in input order), so clicking a "Tests by module" row can scope the whole
	 * Tests page — the trend graph included. Module resolution matches
	 * {@link #testModules}. NB: this loads each run's cases (one query per run); fine for
	 * the on-demand module-scoped view, but a SQL aggregate would scale better on large
	 * histories.
	 */
	public List<int[]> testModuleTrend(List<Long> runIds, String module) {
		List<int[]> out = new ArrayList<>(runIds.size());
		for (Long runId : runIds) {
			List<TestCaseResult> all = cases.findByRunIdOrderByStatusAscClassNameAscNameAsc(runId);
			List<String> mods = moduleOfEach(all);
			int passed = 0;
			int failed = 0;
			for (int i = 0; i < all.size(); i++) {
				if (!module.equals(mods.get(i))) {
					continue;
				}
				TestStatus st = all.get(i).getStatus();
				if (st == TestStatus.PASSED) {
					passed++;
				}
				else if (st == TestStatus.FAILED || st == TestStatus.ERROR) {
					failed++;
				}
			}
			out.add(new int[] { passed, failed });
		}
		return out;
	}

	/**
	 * Per-run {@code [sumDurationMs, testCount]} for one module across the given runs —
	 * the module-scoped Test-timing trend (suite time and how many tests produced it).
	 */
	public List<long[]> moduleTimingTrend(List<Long> runIds, String module) {
		List<long[]> out = new ArrayList<>(runIds.size());
		for (Long runId : runIds) {
			List<TestCaseResult> all = cases.findByRunIdOrderByStatusAscClassNameAscNameAsc(runId);
			List<String> mods = moduleOfEach(all);
			long sumMs = 0;
			long count = 0;
			for (int i = 0; i < all.size(); i++) {
				if (!module.equals(mods.get(i))) {
					continue;
				}
				sumMs += all.get(i).getDurationMs();
				count++;
			}
			out.add(new long[] { sumMs, count });
		}
		return out;
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
		if (coverageFiles.existsByReportIdAndModuleIsNotNull(reportId)) {
			List<CoverageFileEntry> inModule = coverageFiles.findByReportIdAndStoredModule(reportId, storedKey(module));
			return (inModule.size() > limit) ? inModule.subList(0, limit) : inModule;
		}
		List<CoverageFileEntry> inModule = filterByModule(
				coverageFiles.findByReportIdOrderByLineMissedDescPackageNameAsc(reportId), module,
				CoverageFileEntry::getPackageName);
		return (inModule.size() > limit) ? inModule.subList(0, limit) : inModule;
	}

	/** Display label for an explicit module: {@code (none)} for untagged files. */
	private static String moduleLabel(String module) {
		return (module == null || module.isBlank()) ? "(none)" : module;
	}

	/**
	 * Inverse of {@link #moduleLabel}: the {@code (none)} label maps back to a null
	 * module.
	 */
	private static String storedKey(String moduleLabel) {
		return "(none)".equals(moduleLabel) ? null : moduleLabel;
	}

	/** Per-package coverage within one module (null/blank = all packages). */
	public List<CoveragePackage> coveragePackages(Long reportId, String module) {
		if (module != null && !module.isBlank() && coverageFiles.existsByReportIdAndModuleIsNotNull(reportId)) {
			Map<String, long[]> byPackage = new TreeMap<>();
			for (PackageCoverage p : coverageFiles.aggregateByPackageForModule(reportId, storedKey(module))) {
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
	 * Per-module line/branch totals for a coverage report. When the uploader attached
	 * explicit modules (#393) those are used directly; otherwise — for a flat
	 * multi-module upload with no module concept — the module is derived from the package
	 * tree (the segment that follows the longest package prefix common to every package,
	 * e.g. {@code …builder/<module>/…}). Returns at most one entry for a single-module
	 * project, so callers can hide the view. Aggregated in SQL, not per file.
	 */
	public List<ModuleCoverage> moduleCoverage(Long reportId) {
		if (coverageFiles.existsByReportIdAndModuleIsNotNull(reportId)) {
			return coverageFiles.aggregateByModule(reportId)
				.stream()
				.map((m) -> new ModuleCoverage(moduleLabel(m.getModule()), (int) m.getLineCovered(),
						(int) m.getLineMissed(), (int) m.getBranchCovered(), (int) m.getBranchMissed(),
						(int) m.getFiles()))
				.sorted(Comparator.comparing(ModuleCoverage::name))
				.toList();
		}
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
