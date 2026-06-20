package org.alexmond.unitrack.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.CoverageFileEntry;
import org.alexmond.unitrack.domain.CoverageReport;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.CoverageDiff.FileDelta;
import org.alexmond.unitrack.report.CoverageDiff.Kind;
import org.alexmond.unitrack.repository.CoverageFileEntryRepository;
import org.alexmond.unitrack.repository.CoverageReportRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes the per-file line-coverage diff of a run against its baseline — the same
 * baseline the quality gate uses (latest prior run on the base branch with the same
 * flag).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoverageDiffService {

	/** Files whose line-% moved by less than this are treated as unchanged. */
	private static final double EPSILON = 0.05;

	private final TestRunRepository runs;

	private final CoverageReportRepository coverageReports;

	private final CoverageFileEntryRepository coverageFiles;

	private final ProjectSettingsService settings;

	/**
	 * Coverage diff for a run, or empty if the run, its coverage, or a baseline is
	 * missing.
	 */
	@Cacheable(value = "coverageDiff", key = "#runId")
	public Optional<CoverageDiff> diff(Long runId) {
		return runs.findById(runId).flatMap(this::diff);
	}

	private Optional<CoverageDiff> diff(TestRun run) {
		Optional<CoverageReport> current = coverageReports.findByRunId(run.getId());
		if (current.isEmpty()) {
			return Optional.empty();
		}
		GateConfig cfg = settings.gateConfig(run.getProject().getId());
		TestRun baseline = runs
			.findFirstByProjectIdAndBranchAndFlagAndIdNotAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
					run.getProject().getId(), cfg.baseBranch(), run.getFlag(), run.getId(), run.getCreatedAt())
			.orElse(null);
		if (baseline == null) {
			return Optional.empty();
		}
		Optional<CoverageReport> base = coverageReports.findByRunId(baseline.getId());
		if (base.isEmpty()) {
			return Optional.empty();
		}

		Map<String, Double> currentPct = pctByPath(current.get().getId());
		Map<String, Double> basePct = pctByPath(base.get().getId());
		Set<String> allPaths = new TreeSet<>(currentPct.keySet());
		allPaths.addAll(basePct.keySet());

		List<FileDelta> deltas = new ArrayList<>();
		for (String path : allPaths) {
			Double cur = currentPct.get(path);
			Double prev = basePct.get(path);
			if (cur != null && prev != null) {
				double d = cur - prev;
				if (Math.abs(d) >= EPSILON) {
					deltas.add(new FileDelta(path, prev, cur, d, (d > 0) ? Kind.IMPROVED : Kind.DROPPED));
				}
			}
			else if (cur != null) {
				deltas.add(new FileDelta(path, null, cur, cur, Kind.ADDED));
			}
			else {
				deltas.add(new FileDelta(path, prev, null, -prev, Kind.REMOVED));
			}
		}
		// Most negative (biggest drops) first.
		deltas.sort(Comparator.comparingDouble(FileDelta::delta));
		double totalDelta = current.get().getLinePct() - base.get().getLinePct();
		return Optional.of(new CoverageDiff(baseline.getId(), cfg.baseBranch(), totalDelta, deltas));
	}

	private Map<String, Double> pctByPath(Long reportId) {
		Map<String, Double> byPath = new HashMap<>();
		for (CoverageFileEntry f : coverageFiles.findByReportIdOrderByLineMissedDescPackageNameAsc(reportId)) {
			byPath.put(f.getPath(), f.getLinePct());
		}
		return byPath;
	}

}
