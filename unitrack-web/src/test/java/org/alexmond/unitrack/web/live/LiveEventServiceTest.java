package org.alexmond.unitrack.web.live;

import java.time.Instant;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.Visibility;
import org.alexmond.unitrack.web.account.MembershipService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class LiveEventServiceTest {

	private final MembershipService membership = mock(MembershipService.class);

	private final LiveEventService service = new LiveEventService(this.membership);

	private static final RunUpdate UPDATE = new RunUpdate(1L, 2L, "main", "PASSED", 5, 0, 100.0, 80.0, "abc1234",
			"2026-06-16T00:00:00Z");

	@Test
	void publishDeliversOnlyToSubscribersWhoMayReadTheProject() {
		Project project = new Project("live-demo", null);
		project.setVisibility(Visibility.PRIVATE);
		given(this.membership.canRead(null, project)).willReturn(false); // anonymous
		given(this.membership.canRead("alice", project)).willReturn(true); // member

		this.service.subscribe(null);
		this.service.subscribe("alice");
		assertThat(this.service.subscriberCount()).isEqualTo(2);

		// Only the member's stream receives the private project's run.
		assertThat(this.service.publish(project, UPDATE)).isEqualTo(1);
	}

	@Test
	void runUpdateMapsTheRun() {
		TestRun run = mock(TestRun.class);
		Project p = new Project("p", null);
		given(run.getProject()).willReturn(p);
		given(run.getId()).willReturn(9L);
		given(run.getBranch()).willReturn("main");
		given(run.getStatus()).willReturn("FAILED");
		given(run.getTotalTests()).willReturn(10);
		given(run.getFailed()).willReturn(2);
		given(run.getErrors()).willReturn(1);
		given(run.passRate()).willReturn(70.0);
		given(run.getLineCoveragePct()).willReturn(55.5);
		given(run.getShortSha()).willReturn("deadbee");
		given(run.getCreatedAt()).willReturn(Instant.parse("2026-06-16T00:00:00Z"));

		RunUpdate u = RunUpdate.of(run);
		assertThat(u.runId()).isEqualTo(9L);
		assertThat(u.status()).isEqualTo("FAILED");
		assertThat(u.failed()).isEqualTo(3); // failed + errors
		assertThat(u.shortSha()).isEqualTo("deadbee");
	}

}
