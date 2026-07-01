package org.alexmond.unitrack.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestStatus;
import org.alexmond.unitrack.repository.TestCaseResultRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Groups recurring failures by a normalized signature so triage can see "these N failures
 * are all the same root cause" instead of a flat wall of individual failures.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FailureClusteringService {

	private static final List<TestStatus> FAILED = List.of(TestStatus.FAILED, TestStatus.ERROR);

	private static final int MAX_CASES = 2000;

	private static final int MAX_TESTS_PER_CLUSTER = 25;

	private static final Pattern UUID = Pattern
		.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");

	private static final Pattern HEX = Pattern.compile("0x[0-9a-fA-F]+");

	private static final Pattern OBJECT_ID = Pattern.compile("@[0-9a-fA-F]+");

	private static final Pattern NUMBER = Pattern.compile("\\d+");

	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private static final Pattern LINE_BREAK = Pattern.compile("\\R");

	private final TestCaseResultRepository cases;

	/** Clusters recent failures for a project, most frequent first. */
	public List<FailureCluster> cluster(Long projectId) {
		Map<String, Accumulator> clusters = new LinkedHashMap<>();
		for (TestCaseResult c : cases.findRecentFailures(projectId, FAILED, PageRequest.ofSize(MAX_CASES))) {
			clusters.computeIfAbsent(signature(c), (key) -> new Accumulator()).add(c);
		}
		return clusters.entrySet()
			.stream()
			.map((e) -> e.getValue().toCluster(e.getKey()))
			.sorted(Comparator.comparingInt(FailureCluster::occurrences).reversed())
			.toList();
	}

	/** Builds a normalized signature from failure type, message and top stack frame. */
	static String signature(TestCaseResult c) {
		String type = (c.getFailureType() != null) ? c.getFailureType() : "";
		String message = normalize(firstLine(c.getFailureMessage()));
		String frame = normalize(topFrame(c.getFailureStacktrace()));
		String sig = type + " | " + message + " | " + frame;
		return (sig.length() <= 500) ? sig : sig.substring(0, 500);
	}

	static String normalize(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		String s = value.strip();
		s = UUID.matcher(s).replaceAll("<uuid>");
		s = HEX.matcher(s).replaceAll("0x#");
		s = OBJECT_ID.matcher(s).replaceAll("@#");
		s = NUMBER.matcher(s).replaceAll("#");
		return WHITESPACE.matcher(s).replaceAll(" ");
	}

	private static String firstLine(String text) {
		if (text == null) {
			return null;
		}
		for (String line : LINE_BREAK.split(text)) {
			if (!line.isBlank()) {
				return line.strip();
			}
		}
		return null;
	}

	/** First "at ..." stack frame, else the first non-blank line. */
	private static String topFrame(String stacktrace) {
		if (stacktrace == null) {
			return null;
		}
		String firstNonBlank = null;
		for (String line : LINE_BREAK.split(stacktrace)) {
			String trimmed = line.strip();
			if (trimmed.isBlank()) {
				continue;
			}
			if (firstNonBlank == null) {
				firstNonBlank = trimmed;
			}
			if (trimmed.startsWith("at ")) {
				return trimmed;
			}
		}
		return firstNonBlank;
	}

	private static final class Accumulator {

		private String failureType;

		private String sampleMessage;

		private int occurrences;

		private Instant lastSeen;

		private final Set<String> tests = new LinkedHashSet<>();

		void add(TestCaseResult c) {
			occurrences++;
			tests.add(testName(c));
			Instant created = c.getRun().getCreatedAt();
			if (lastSeen == null || created.isAfter(lastSeen)) {
				lastSeen = created;
			}
			if (sampleMessage == null) {
				failureType = c.getFailureType();
				sampleMessage = firstLine(c.getFailureMessage());
			}
		}

		FailureCluster toCluster(String signature) {
			List<String> sample = new ArrayList<>(tests).stream().limit(MAX_TESTS_PER_CLUSTER).toList();
			return new FailureCluster(signature, failureType, sampleMessage, occurrences, tests.size(), sample,
					lastSeen);
		}

		private static String testName(TestCaseResult c) {
			String cls = (c.getClassName() != null) ? c.getClassName() : "";
			return cls.isBlank() ? c.getName() : cls + "#" + c.getName();
		}

	}

}
