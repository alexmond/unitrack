package org.alexmond.unitrack.report;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.FlakyStatus;
import org.alexmond.unitrack.domain.FlakyTest;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.repository.FlakyTestRepository;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Detects flaky tests and tracks their quarantine state. */
@Service
@RequiredArgsConstructor
public class FlakyTestService {

	private final FlakyTestRepository flakyTests;

	private final ProjectRepository projects;

	/**
	 * Live-detected flaky tests for a project, merged with any stored quarantine state.
	 */
	@Transactional(readOnly = true)
	public List<FlakyTestView> listFlaky(Long projectId) {
		Map<String, FlakyTest> stored = new HashMap<>();
		for (FlakyTest ft : flakyTests.findByProjectId(projectId)) {
			stored.put(key(ft.getClassName(), ft.getName()), ft);
		}
		return flakyTests.findFlakyStats(projectId).stream().map((stat) -> toView(stat, stored)).toList();
	}

	/**
	 * Count of live-detected flaky tests for a project — runs only the detection query,
	 * without loading stored quarantine rows or building views (for the health board).
	 */
	@Transactional(readOnly = true)
	public long flakyCount(Long projectId) {
		return flakyTests.findFlakyStats(projectId).size();
	}

	private FlakyTestView toView(FlakyStat stat, Map<String, FlakyTest> stored) {
		FlakyTest ft = stored.get(key(stat.getClassName(), stat.getName()));
		FlakyStatus status = (ft != null) ? ft.getStatus() : FlakyStatus.ACTIVE;
		String note = (ft != null) ? ft.getNote() : null;
		double rate = (stat.getTotalResults() == 0) ? 0.0 : (stat.getFailures() * 100.0) / stat.getTotalResults();
		LocalDateTime last = stat.getLastFailureAt();
		return new FlakyTestView(stat.getClassName(), stat.getName(), stat.getFlakyCommits(), stat.getTotalResults(),
				stat.getFailures(), rate, (last != null) ? last.toInstant(ZoneOffset.UTC) : null, status, note);
	}

	/** Sets the quarantine/resolution state for a test, creating the record if needed. */
	@Transactional
	public FlakyTest setStatus(Long projectId, String className, String name, FlakyStatus status, String note) {
		FlakyTest ft = flakyTests.findOne(projectId, className, name).orElseGet(() -> {
			Project project = projects.findById(projectId)
				.orElseThrow(() -> new IllegalArgumentException("Unknown project " + projectId));
			return new FlakyTest(project, className, name);
		});
		ft.update(status, note);
		return flakyTests.save(ft);
	}

	private static String key(String className, String name) {
		return ((className != null) ? className : "") + " " + name;
	}

}
