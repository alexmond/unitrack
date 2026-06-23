package org.alexmond.unitrack.report;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.repository.TestCaseResultRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side performance reporting over the per-test {@code durationMs} that ingestion
 * already stores: slowest-tests leaderboards and duration trends. No new data is
 * captured.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PerformanceService {

	private final TestRunRepository runs;

	private final TestCaseResultRepository cases;

	/** The slowest test cases in a run, longest first. */
	public List<SlowTest> slowestInRun(Long runId, int limit) {
		return this.cases.findByRunIdOrderByDurationMsDescNameAsc(runId, PageRequest.ofSize(limit))
			.stream()
			.map(SlowTest::of)
			.toList();
	}

	/** Total suite-time trend for a project, oldest run first (for charting). */
	public List<DurationPoint> suiteTimeTrend(Long projectId, int limit) {
		return suiteTimeTrend(projectId, null, limit);
	}

	/**
	 * Suite-time trend scoped to one flag, oldest run first. A split-by-module project
	 * uploads a run per module plus a {@code default} rollup, each with very different
	 * totals; charting all flags together interleaves them into a sawtooth (same trap as
	 * the coverage trend, #280). Pass the rollup flag for one coherent series; null/blank
	 * charts every run (legacy).
	 */
	public List<DurationPoint> suiteTimeTrend(Long projectId, String flag, int limit) {
		PageRequest page = PageRequest.ofSize(limit);
		List<TestRun> recent = (flag == null || flag.isBlank())
				? this.runs.findByProjectIdOrderByCreatedAtDesc(projectId, page)
				: this.runs.findByProjectIdAndFlagOrderByCreatedAtDesc(projectId, flag, page);
		return recent.reversed().stream().map(DurationPoint::ofRun).toList();
	}

	/**
	 * Project performance summary: suite-time trend + slowest tests in the latest run.
	 */
	public PerformanceSummary summary(Long projectId, int slowLimit, int trendLimit) {
		return summary(projectId, null, slowLimit, trendLimit);
	}

	/**
	 * Performance summary scoped to one flag: the suite-time trend and the "latest run"
	 * (for the slowest-tests table) both restrict to that flag, so a split-by-module
	 * project charts a coherent rollup series instead of interleaved per-module runs.
	 * Null/blank = every flag.
	 */
	public PerformanceSummary summary(Long projectId, String flag, int slowLimit, int trendLimit) {
		PageRequest one = PageRequest.ofSize(1);
		Optional<TestRun> latest = ((flag == null || flag.isBlank())
				? this.runs.findByProjectIdOrderByCreatedAtDesc(projectId, one)
				: this.runs.findByProjectIdAndFlagOrderByCreatedAtDesc(projectId, flag, one))
			.stream()
			.findFirst();
		List<DurationPoint> trend = suiteTimeTrend(projectId, flag, trendLimit);
		List<SlowTest> slowest = latest.map((run) -> slowestInRun(run.getId(), slowLimit)).orElse(List.of());
		return new PerformanceSummary(projectId, latest.map(TestRun::getId).orElse(null), trend, slowest);
	}

	/** One test's duration across a project's recent runs, oldest first. */
	public TestDurationTrend testDurationTrend(Long projectId, String className, String name, int limit) {
		List<DurationPoint> points = this.cases.findTestHistory(projectId, className, name, PageRequest.ofSize(limit))
			.reversed()
			.stream()
			.map(DurationPoint::ofCase)
			.toList();
		return new TestDurationTrend(className, name, points);
	}

	/**
	 * One test's status+duration timeline across a project's recent runs, oldest first.
	 * Reuses the existing {@code findTestHistory} query — no new repository method or
	 * schema change.
	 */
	public List<TestTimelinePoint> testStatusTimeline(Long projectId, String className, String name, int limit) {
		return testStatusTimeline(projectId, className, name, null, limit);
	}

	/**
	 * One test's status+duration timeline scoped to a single flag. Without scoping, a
	 * split-by-module project plots the test twice per commit (its module run + the
	 * {@code
	 * default} rollup both contain it). Null/blank flag = every run (legacy).
	 */
	public List<TestTimelinePoint> testStatusTimeline(Long projectId, String className, String name, String flag,
			int limit) {
		PageRequest page = PageRequest.ofSize(limit);
		List<org.alexmond.unitrack.domain.TestCaseResult> history = (flag == null || flag.isBlank())
				? this.cases.findTestHistory(projectId, className, name, page)
				: this.cases.findTestHistoryForFlag(projectId, className, name, flag, page);
		return history.reversed().stream().map(TestTimelinePoint::ofCase).toList();
	}

}
