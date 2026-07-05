package org.alexmond.unitrack.web.ui.view;

import java.time.Instant;
import java.util.List;

import org.alexmond.unitrack.domain.Project;

/**
 * The per-transaction detail page ({@code /projects/{id}/perf/transaction?label=…}) — one
 * request label's performance over runs. Reached by clicking a row in the Load tests "By
 * transaction" table; mirrors the per-run and per-test detail pages (KPI tiles + a trend
 * + a history table). Built by {@code LoadPageService.buildTransaction}.
 *
 * @param project the owning project
 * @param tab the active subnav tab ("load" — this drills off Load tests)
 * @param label the transaction/request label being detailed
 * @param selectedFlag the perf flag (series) this history is scoped to
 * @param hasRun whether any data exists for this label+flag (else an empty state)
 * @param kpis the latest run's p95 (+Δ) / p50 / p99 / samples tiles
 * @param trend the latency-over-runs chart (p50 / p95 / p99)
 * @param runs the per-run history rows, newest first
 * @param repoCommitBase the commit-link base ({@code <repo>/commit/}), or null when
 * unknown
 */
public record PerfTransactionPage(Project project, String tab, String label, String selectedFlag, boolean hasRun,
		List<KpiTile> kpis, TrendView trend, List<RunRow> runs, String repoCommitBase) {

	/**
	 * One run in the transaction's history table.
	 *
	 * @param runId the perf-run id (links to its detail page)
	 * @param commitSha the run's commit, or null
	 * @param createdAt when the run was ingested
	 * @param p50Ms p50 latency for this label in that run
	 * @param p95Ms p95 latency for this label in that run
	 * @param p99Ms p99 latency for this label in that run
	 * @param sampleCount samples for this label in that run
	 * @param errorPct error percentage for this label in that run
	 */
	public record RunRow(Long runId, String commitSha, Instant createdAt, double p50Ms, double p95Ms, double p99Ms,
			long sampleCount, double errorPct) {
	}

}
