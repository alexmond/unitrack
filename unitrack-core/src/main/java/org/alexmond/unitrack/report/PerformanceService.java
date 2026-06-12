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
		List<TestRun> recent = this.runs.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.ofSize(limit));
		return recent.reversed().stream().map(DurationPoint::ofRun).toList();
	}

	/**
	 * Project performance summary: suite-time trend + slowest tests in the latest run.
	 */
	public PerformanceSummary summary(Long projectId, int slowLimit, int trendLimit) {
		Optional<TestRun> latest = this.runs.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.ofSize(1))
			.stream()
			.findFirst();
		List<DurationPoint> trend = suiteTimeTrend(projectId, trendLimit);
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
		return this.cases.findTestHistory(projectId, className, name, PageRequest.ofSize(limit))
			.reversed()
			.stream()
			.map(TestTimelinePoint::ofCase)
			.toList();
	}

}
