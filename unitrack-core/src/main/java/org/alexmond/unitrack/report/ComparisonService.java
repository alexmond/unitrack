package org.alexmond.unitrack.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.TestStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Diffs two runs by test status, with coverage / pass-rate / duration deltas. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComparisonService {

	private final ReportingService reporting;

	/** Compares head against base; empty if either run is missing. */
	public Optional<RunComparison> compare(Long baseId, Long headId) {
		Optional<TestRun> baseRun = reporting.findRun(baseId);
		Optional<TestRun> headRun = reporting.findRun(headId);
		if (baseRun.isEmpty() || headRun.isEmpty()) {
			return Optional.empty();
		}
		TestRun base = baseRun.get();
		TestRun head = headRun.get();
		Map<String, TestStatus> baseStatus = byName(reporting.allCasesFor(baseId));
		Map<String, TestStatus> headStatus = byName(reporting.allCasesFor(headId));

		List<String> newlyFailing = new ArrayList<>();
		List<String> fixed = new ArrayList<>();
		List<String> stillFailing = new ArrayList<>();
		TreeSet<String> keys = new TreeSet<>();
		keys.addAll(baseStatus.keySet());
		keys.addAll(headStatus.keySet());
		for (String key : keys) {
			boolean baseFailing = failing(baseStatus.get(key));
			boolean headFailing = failing(headStatus.get(key));
			if (headFailing && !baseFailing) {
				newlyFailing.add(key);
			}
			else if (baseFailing && !headFailing && headStatus.containsKey(key)) {
				fixed.add(key);
			}
			else if (baseFailing && headFailing) {
				stillFailing.add(key);
			}
		}

		Double coverageDelta = (base.getLineCoveragePct() != null && head.getLineCoveragePct() != null)
				? head.getLineCoveragePct() - base.getLineCoveragePct() : null;
		long durationDelta = head.getDurationMs() - base.getDurationMs();
		double passRateDelta = head.passRate() - base.passRate();
		return Optional.of(new RunComparison(base, head, newlyFailing, fixed, stillFailing, coverageDelta,
				durationDelta, passRateDelta));
	}

	private static Map<String, TestStatus> byName(List<TestCaseResult> cases) {
		Map<String, TestStatus> map = new LinkedHashMap<>();
		for (TestCaseResult c : cases) {
			String key = (c.getClassName() == null || c.getClassName().isBlank()) ? c.getName()
					: c.getClassName() + "#" + c.getName();
			map.put(key, c.getStatus());
		}
		return map;
	}

	private static boolean failing(TestStatus status) {
		return status == TestStatus.FAILED || status == TestStatus.ERROR;
	}

}
