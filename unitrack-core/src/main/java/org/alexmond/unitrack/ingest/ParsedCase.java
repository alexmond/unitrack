package org.alexmond.unitrack.ingest;

import org.alexmond.unitrack.domain.TestStatus;

/** A single parsed &lt;testcase&gt; before persistence. */
public record ParsedCase(String suiteName, String className, String name, TestStatus status, long durationMs,
		String failureType, String failureMessage, String failureStacktrace) {
}
