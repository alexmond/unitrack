package org.alexmond.unitrack.web.ai;

/**
 * LLM analysis of a failure cluster: the likely root cause, a fix direction, and the
 * model's confidence. Produced once per failure signature and cached.
 */
public record FailureAnalysis(String rootCause, String suggestion, String confidence, String model) {
}
