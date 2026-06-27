package org.alexmond.unitrack.report;

import java.time.Instant;
import java.util.List;

import org.alexmond.unitrack.domain.Visibility;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The board ordering (the headline UX of #328): regressed projects first, longest-broken
 * on top, then the existing rank (failing gate, passing, no-runs) and name.
 */
class ProjectHealthBoardOrderTest {

	@Test
	void ordersRegressedFirstThenLongestBrokenThenRankThenName() {
		ProjectHealth rotOld = regressed("rot-old", 14);
		ProjectHealth rotNew = regressed("rot-new", 3);
		ProjectHealth gateFail = withRuns("gate-fail", "FAILED");
		ProjectHealth green = withRuns("green", "PASSED");
		ProjectHealth noRuns = noRuns("norun");

		List<String> order = List.of(green, noRuns, rotNew, gateFail, rotOld)
			.stream()
			.sorted(ProjectHealthService.BOARD_ORDER)
			.map(ProjectHealth::projectName)
			.toList();

		// regressed first by days-red desc, then gate-failing, then passing, then
		// no-runs.
		assertThat(order).containsExactly("rot-old", "rot-new", "gate-fail", "green", "norun");
	}

	@Test
	void breaksTiesByNameAmongEquallyHealthyProjects() {
		List<String> order = List.of(withRuns("zebra", "PASSED"), withRuns("alpha", "PASSED"))
			.stream()
			.sorted(ProjectHealthService.BOARD_ORDER)
			.map(ProjectHealth::projectName)
			.toList();
		assertThat(order).containsExactly("alpha", "zebra");
	}

	private static ProjectHealth regressed(String name, long daysRed) {
		return new ProjectHealth(1L, name, 2L, Instant.now(), "main", "FAILED", 50.0, 70.0, 0, -1, Visibility.PUBLIC,
				Instant.now().minusSeconds(daysRed * 86_400), daysRed, daysRed);
	}

	private static ProjectHealth withRuns(String name, String gateStatus) {
		return new ProjectHealth(1L, name, 2L, Instant.now(), "main", gateStatus, 100.0, 80.0, 0, 0, Visibility.PUBLIC,
				null, 0, 0);
	}

	private static ProjectHealth noRuns(String name) {
		return new ProjectHealth(1L, name, null, null, null, null, null, null, 0, 0, Visibility.PUBLIC, null, 0, 0);
	}

}
