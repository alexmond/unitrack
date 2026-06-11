package org.alexmond.unitrack.report;

import java.util.Comparator;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the global all-projects health board: each project's latest-run gate status,
 * pass%, coverage%, flaky count and pass-rate trend, sorted trouble-first (failing gate,
 * then no-runs last, then by name).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectHealthService {

	private final ReportingService reporting;

	private final QualityGateService qualityGate;

	private final FlakyTestService flaky;

	public List<ProjectHealth> board() {
		return this.reporting.listProjects()
			.stream()
			.map(this::health)
			.sorted(Comparator.comparingInt(ProjectHealthService::rank).thenComparing(ProjectHealth::projectName))
			.toList();
	}

	private ProjectHealth health(Project project) {
		Long id = project.getId();
		List<TestRun> recent = this.reporting.recentRuns(id, 2);
		long flakyCount = this.flaky.flakyCount(id);
		if (recent.isEmpty()) {
			return new ProjectHealth(id, project.getName(), null, null, null, null, null, null, flakyCount, 0);
		}
		TestRun latest = recent.get(0);
		String gateStatus = this.qualityGate.evaluate(latest).status();
		return new ProjectHealth(id, project.getName(), latest.getId(), latest.getCreatedAt(), latest.getBranch(),
				gateStatus, latest.passRate(), latest.getLineCoveragePct(), flakyCount, trend(recent));
	}

	/** Pass-rate direction of the latest run vs the prior one: +1 up, -1 down, 0 flat. */
	private static int trend(List<TestRun> recent) {
		if (recent.size() < 2) {
			return 0;
		}
		return (int) Math.signum(recent.get(0).passRate() - recent.get(1).passRate());
	}

	/** 0 = gate failing, 1 = gate passing, 2 = no runs — so trouble sorts to the top. */
	private static int rank(ProjectHealth health) {
		if (!health.hasRuns()) {
			return 2;
		}
		return health.gatePassed() ? 1 : 0;
	}

}
