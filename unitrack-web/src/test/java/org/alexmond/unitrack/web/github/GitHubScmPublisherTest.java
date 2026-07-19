package org.alexmond.unitrack.web.github;

import java.util.List;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.QualityGateResult;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/** The GitHub fan-out: check run when the App is configured, commit status otherwise. */
class GitHubScmPublisherTest {

	private final GitHubCheckRunService checkRun = mock(GitHubCheckRunService.class);

	private final GitHubStatusService status = mock(GitHubStatusService.class);

	private final GitHubPrCommentService prComment = mock(GitHubPrCommentService.class);

	private final GitHubScmPublisher publisher = new GitHubScmPublisher(this.checkRun, this.status, this.prComment);

	private final TestRun run = new TestRun(new Project("demo", "https://github.com/octo/repo"), "main", "default",
			"abc123", null, null);

	private final QualityGateResult gate = new QualityGateResult(true, List.of());

	@Test
	void namesItsProvider() {
		assertThat(this.publisher.providerName()).isEqualTo("GitHub");
	}

	@Test
	void postsOnlyTheCheckRunWhenItIsAvailable() {
		given(this.checkRun.publish(any(), any(), any(), eq(2))).willReturn(true);

		this.publisher.publishRun(this.run, this.gate, 1.5, 2, 1);

		then(this.status).should(never()).publish(any(), any(), any());
		then(this.prComment).should().publish(this.run, this.gate, 1.5, 2, 1);
	}

	@Test
	void fallsBackToTheCommitStatusWhenThereIsNoCheckRun() {
		// PAT-only deployment: the Checks API rejects PATs, so the check run declines.
		given(this.checkRun.publish(any(), any(), any(), eq(2))).willReturn(false);

		this.publisher.publishRun(this.run, this.gate, 1.5, 2, 1);

		InOrder order = inOrder(this.checkRun, this.status, this.prComment);
		order.verify(this.checkRun).publish(this.run, this.gate, 1.5, 2);
		order.verify(this.status).publish(this.run, this.gate, 1.5);
		order.verify(this.prComment).publish(this.run, this.gate, 1.5, 2, 1);
	}

}
