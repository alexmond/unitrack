package org.alexmond.unitrack.web.scm;

import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.QualityGateResult;

/**
 * Publishes a run's quality-gate verdict back to the source-control provider that hosts
 * the project's repository — a commit status, and whatever richer surface that provider
 * offers (a GitHub check run + PR comment, a GitLab MR note).
 * <p>
 * One bean per provider. Each implementation decides for itself whether a given run is
 * its business (from the project's repo URL and its own configuration) and returns
 * quietly when it is not, so the ingest path fans out to every publisher without knowing
 * which providers exist. Implementations are best-effort: a provider being down must
 * never fail an ingest.
 */
public interface ScmPublisher {

	/** The provider's name, for logging (e.g. {@code GitHub}). */
	String providerName();

	/**
	 * Publishes everything this provider surfaces for the run. Returns quietly when the
	 * run's repository isn't this provider's, or the integration is disabled.
	 * @param run the ingested run
	 * @param gate its quality-gate verdict, or null when no gate is configured
	 * @param coverageDelta line-coverage change vs the baseline, in percentage points, or
	 * null when there is no baseline
	 * @param newFailures count of tests failing that didn't fail on the baseline
	 * @param slowerTests count of tests that regressed on duration vs the baseline
	 */
	void publishRun(TestRun run, QualityGateResult gate, Double coverageDelta, int newFailures, int slowerTests);

}
