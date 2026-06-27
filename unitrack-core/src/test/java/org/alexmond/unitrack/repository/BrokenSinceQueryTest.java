package org.alexmond.unitrack.repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The set-based "broken since" board query — onset, last-green and red-run count. */
@SpringBootTest
@Transactional
class BrokenSinceQueryTest {

	@Autowired
	private ProjectRepository projects;

	@Autowired
	private TestRunRepository runs;

	private final Instant now = Instant.now();

	@Test
	void reportsCurrentRedStreakSinceLastGreen() {
		Project p = newProject("bs-streak");
		run(p, "main", true, daysAgo(20));
		run(p, "main", true, daysAgo(14)); // last green
		run(p, "main", false, daysAgo(13)); // onset
		run(p, "main", false, daysAgo(7));
		run(p, "main", false, daysAgo(1)); // latest, red

		BrokenSince b = brokenFor(p);
		assertThat(b.getLastGreenAt()).isCloseTo(daysAgo(14), within(2, ChronoUnit.SECONDS));
		assertThat(b.getBrokenSince()).isCloseTo(daysAgo(13), within(2, ChronoUnit.SECONDS));
		assertThat(b.getRunsRed()).isEqualTo(3);
	}

	@Test
	void omitsProjectsWhoseLatestRunPassed() {
		Project p = newProject("bs-recovered");
		run(p, "main", false, daysAgo(5));
		run(p, "main", true, daysAgo(1)); // recovered -> latest green
		assertThat(runs.findBrokenSince()).noneMatch((b) -> p.getId().equals(b.getProjectId()));
	}

	@Test
	void handlesNeverGreenSeries() {
		Project p = newProject("bs-nevergreen");
		run(p, "main", false, daysAgo(9));
		run(p, "main", false, daysAgo(2)); // latest red

		BrokenSince b = brokenFor(p);
		assertThat(b.getLastGreenAt()).isNull();
		assertThat(b.getBrokenSince()).isCloseTo(daysAgo(9), within(2, ChronoUnit.SECONDS));
		assertThat(b.getRunsRed()).isEqualTo(2);
	}

	@Test
	void ignoresGreenOnADifferentBranchWhenComputingTheStreak() {
		Project p = newProject("bs-branchscope");
		run(p, "feature", true, daysAgo(3)); // green, but on a different branch
		run(p, "main", false, daysAgo(10));
		run(p, "main", false, daysAgo(2)); // latest = main red

		BrokenSince b = brokenFor(p);
		// the feature-branch green must not count as the main series' last green
		assertThat(b.getLastGreenAt()).isNull();
		assertThat(b.getBrokenSince()).isCloseTo(daysAgo(10), within(2, ChronoUnit.SECONDS));
		assertThat(b.getRunsRed()).isEqualTo(2);
	}

	private Project newProject(String name) {
		return projects.save(new Project(name, "https://example.test/" + name));
	}

	private void run(Project p, String branch, boolean green, Instant at) {
		TestRun r = new TestRun(p, branch, "default", "sha", null, null);
		if (green) {
			r.applyTotals(10, 0, 0, 0, 100);
		}
		else {
			r.applyTotals(8, 2, 0, 0, 100);
		}
		ReflectionTestUtils.setField(r, "createdAt", at);
		runs.save(r);
	}

	private BrokenSince brokenFor(Project p) {
		return runs.findBrokenSince()
			.stream()
			.filter((b) -> p.getId().equals(b.getProjectId()))
			.findFirst()
			.orElseThrow();
	}

	private Instant daysAgo(int d) {
		return this.now.minus(d, ChronoUnit.DAYS);
	}

}
