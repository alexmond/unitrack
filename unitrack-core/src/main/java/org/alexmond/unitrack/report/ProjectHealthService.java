package org.alexmond.unitrack.report;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Predicate;

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
		return board((project) -> true);
	}

	/**
	 * The board, restricted to projects the caller may read (visibility/membership
	 * filter).
	 */
	public List<ProjectHealth> board(Predicate<Project> include) {
		// Two batch queries for the whole board instead of run + flaky queries per
		// project.
		Map<Long, List<TestRun>> runsByProject = this.reporting.latestRunsByProject();
		Map<Long, Long> flakyByProject = this.flaky.flakyCountsByProject();
		return this.reporting.listProjects()
			.stream()
			.filter(include)
			.map((project) -> health(project, runsByProject.getOrDefault(project.getId(), List.of()),
					flakyByProject.getOrDefault(project.getId(), 0L)))
			.sorted(Comparator.comparingInt(ProjectHealthService::rank).thenComparing(ProjectHealth::projectName))
			.toList();
	}

	/** Aggregate KPIs across the board rows, derived in memory — no extra queries. */
	public static BoardSummary summarize(List<ProjectHealth> board) {
		long failingGates = board.stream().filter(ProjectHealth::hasRuns).filter((h) -> !h.gatePassed()).count();
		long flakyTotal = board.stream().mapToLong(ProjectHealth::flakyCount).sum();
		OptionalDouble avg = board.stream()
			.map(ProjectHealth::coveragePct)
			.filter(Objects::nonNull)
			.mapToDouble(Double::doubleValue)
			.average();
		return new BoardSummary(board.size(), failingGates, flakyTotal, avg.isPresent() ? avg.getAsDouble() : null);
	}

	private ProjectHealth health(Project project, List<TestRun> recent, long flakyCount) {
		Long id = project.getId();
		if (recent.isEmpty()) {
			return new ProjectHealth(id, project.getName(), null, null, null, null, null, null, flakyCount, 0,
					project.getVisibility());
		}
		TestRun latest = recent.get(0);
		String gateStatus = this.qualityGate.evaluate(latest).status();
		return new ProjectHealth(id, project.getName(), latest.getId(), latest.getCreatedAt(), latest.getBranch(),
				gateStatus, latest.passRate(), latest.getLineCoveragePct(), flakyCount, trend(recent),
				project.getVisibility());
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
