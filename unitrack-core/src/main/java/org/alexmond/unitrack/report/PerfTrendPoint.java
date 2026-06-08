package org.alexmond.unitrack.report;

import java.time.Instant;

import org.alexmond.unitrack.domain.PerfRun;

/** One point on a project's performance trend (one perf run). */
public record PerfTrendPoint(Long runId, String shortSha, Instant createdAt, double p50Ms, double p90Ms, double p95Ms,
		double p99Ms, double throughputRps, double errorPct) {

	public static PerfTrendPoint of(PerfRun r) {
		String sha = r.getCommitSha();
		String shortSha = (sha != null && sha.length() > 7) ? sha.substring(0, 7) : sha;
		return new PerfTrendPoint(r.getId(), shortSha, r.getCreatedAt(), r.getP50Ms(), r.getP90Ms(), r.getP95Ms(),
				r.getP99Ms(), r.getThroughputRps(), r.getErrorPct());
	}
}
