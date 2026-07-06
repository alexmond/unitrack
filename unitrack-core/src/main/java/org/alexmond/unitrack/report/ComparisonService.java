package org.alexmond.unitrack.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

	private static final int MAX_TIMING_ROWS = 50;

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
		List<TestCaseResult> baseCases = reporting.allCasesFor(baseId);
		List<TestCaseResult> headCases = reporting.allCasesFor(headId);
		Map<String, TestStatus> baseStatus = byName(baseCases);
		Map<String, TestStatus> headStatus = byName(headCases);

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
				durationDelta, passRateDelta, timingDeltas(baseCases, headCases)));
	}

	/**
	 * Per-test duration changes, biggest absolute change first (capped) — explains a
	 * suite-time move: which tests got slower/faster, dropped out, or were added.
	 */
	private static List<TestTimingDelta> timingDeltas(List<TestCaseResult> baseCases, List<TestCaseResult> headCases) {
		Map<String, Long> base = durations(baseCases);
		Map<String, Long> head = durations(headCases);
		Set<String> keys = new LinkedHashSet<>();
		keys.addAll(head.keySet());
		keys.addAll(base.keySet());
		List<TestTimingDelta> deltas = new ArrayList<>();
		for (String key : keys) {
			Long b = base.get(key);
			Long h = head.get(key);
			if (b != null && h != null) {
				long d = h - b;
				if (d != 0) {
					deltas.add(new TestTimingDelta(key, b, h, d,
							(d > 0) ? TestTimingDelta.Kind.SLOWER : TestTimingDelta.Kind.FASTER));
				}
			}
			else if (h != null) {
				deltas.add(new TestTimingDelta(key, null, h, h, TestTimingDelta.Kind.ADDED));
			}
			else if (b != null) {
				deltas.add(new TestTimingDelta(key, b, null, -b, TestTimingDelta.Kind.REMOVED));
			}
		}
		deltas.sort(Comparator.comparingLong((TestTimingDelta t) -> Math.abs(t.deltaMs())).reversed());
		return (deltas.size() > MAX_TIMING_ROWS) ? new ArrayList<>(deltas.subList(0, MAX_TIMING_ROWS)) : deltas;
	}

	private static Map<String, Long> durations(List<TestCaseResult> cases) {
		Map<String, Long> map = new LinkedHashMap<>();
		for (TestCaseResult c : cases) {
			String key = (c.getClassName() == null || c.getClassName().isBlank()) ? c.getName()
					: c.getClassName() + "#" + c.getName();
			map.put(key, c.getDurationMs());
		}
		return map;
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
