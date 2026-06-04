package io.github.alexmond.unitrack.ingest;

import io.github.alexmond.unitrack.domain.TestStatus;

/** A single parsed &lt;testcase&gt; before persistence. */
public record ParsedCase(
        String suiteName,
        String className,
        String name,
        TestStatus status,
        long durationMs,
        String failureType,
        String failureMessage,
        String failureStacktrace) {
}
