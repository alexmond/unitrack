package org.alexmond.unitrack.report;

import java.time.Instant;

/** Latest coverage/status for one coverage flag (component) of a project. */
public record FlagSummary(String flag, Double lineCoveragePct, Long lastRunId, Instant lastRunAt, String lastStatus) {
}
