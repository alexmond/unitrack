package org.alexmond.unitrack.report;

import java.time.Instant;
import java.util.List;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A trend series must be strictly chronological so the "Over time" chart never retraces
 * horizontally ("back in time"): {@code trend.js} connects points in array order on a
 * linear time axis, so the x-values ({@code createdAt}) it is handed must be
 * non-decreasing. Two guarantees are checked: same-timestamp runs come back ordered by a
 * stable {@code id} tiebreaker, and a run whose {@code createdAt} is earlier than a
 * later-inserted one is still placed earlier (time wins over insertion order).
 */
@SpringBootTest
@Transactional
class TrendChronologicalOrderTest {

	@Autowired
	private ProjectRepository projects;

	@Autowired
	private TestRunRepository runs;

	@Autowired
	private ReportingService reporting;

	private TestRun run(Project p, String sha, Instant createdAt) {
		TestRun r = new TestRun(p, "main", "default", sha, null, null);
		// createdAt is intentionally not settable in production; force it here to model
		// same-timestamp / out-of-insertion-order runs.
		ReflectionTestUtils.setField(r, "createdAt", createdAt);
		r.applyTotals(10, 0, 0, 0, 1000);
		return this.runs.save(r);
	}

	@Test
	void tiesBreakByIdSoTheSeriesIsMonotonic() {
		Project p = this.projects.save(new Project("trend-ties", "https://github.com/octo/repo"));
		Instant sameInstant = Instant.parse("2026-07-18T05:32:00Z");
		// Three runs at the identical timestamp — without an id tiebreaker their order
		// (and the LIMIT window) is unstable, and the reversed series can zigzag in x.
		TestRun a = run(p, "aaa", sameInstant);
		TestRun b = run(p, "bbb", sameInstant);
		TestRun c = run(p, "ccc", sameInstant);

		List<TestRun> trend = this.reporting.trendRuns(p.getId(), null, "default", 30);

		assertThat(trend).extracting(TestRun::getId).containsExactly(a.getId(), b.getId(), c.getId());
		assertThat(trend).extracting((r) -> r.getCreatedAt().toEpochMilli()).isSorted();
	}

	@Test
	void earlierTimestampComesFirstEvenWhenInsertedLater() {
		Project p = this.projects.save(new Project("trend-order", "https://github.com/octo/repo"));
		// Insert a LATER-timestamped run first, then an EARLIER one — so insertion order
		// (id) disagrees with chronological order. The series must follow time, not id.
		TestRun later = run(p, "late", Instant.parse("2026-07-18T05:32:00Z"));
		TestRun earlier = run(p, "early", Instant.parse("2026-07-17T09:00:00Z"));

		List<TestRun> trend = this.reporting.trendRuns(p.getId(), null, "default", 30);

		assertThat(trend).extracting(TestRun::getId).containsExactly(earlier.getId(), later.getId());
		assertThat(trend).extracting((r) -> r.getCreatedAt().toEpochMilli()).isSorted();
	}

}
