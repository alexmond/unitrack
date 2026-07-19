package org.alexmond.unitrack.web.github;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.QualityGateResult;
import org.alexmond.unitrack.web.scm.ScmPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * The GitHub side of the ingest fan-out: a check run (or a commit status, when the
 * deployment has no App) plus the PR results comment. Owns the choice between the two
 * status surfaces so the ingest path doesn't have to know GitHub has one.
 */
@Service
@Order(10)
@RequiredArgsConstructor
public class GitHubScmPublisher implements ScmPublisher {

	private final GitHubCheckRunService checkRun;

	private final GitHubStatusService status;

	private final GitHubPrCommentService prComment;

	@Override
	public String providerName() {
		return "GitHub";
	}

	@Override
	public void publishRun(TestRun run, QualityGateResult gate, Double coverageDelta, int newFailures,
			int slowerTests) {
		// App deployments get a rich check run (summary + inline PR annotations);
		// PAT-only
		// deployments fall back to the classic commit status.
		if (!this.checkRun.publish(run, gate, coverageDelta, newFailures)) {
			this.status.publish(run, gate, coverageDelta);
		}
		this.prComment.publish(run, gate, coverageDelta, newFailures, slowerTests);
	}

}
