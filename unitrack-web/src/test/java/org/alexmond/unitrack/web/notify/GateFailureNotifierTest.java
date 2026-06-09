package org.alexmond.unitrack.web.notify;

import java.util.List;
import java.util.Optional;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.ProjectMembership;
import org.alexmond.unitrack.domain.Role;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.User;
import org.alexmond.unitrack.report.GateConfig;
import org.alexmond.unitrack.report.ProjectSettingsService;
import org.alexmond.unitrack.report.QualityGateResult;
import org.alexmond.unitrack.report.QualityGateResult.RuleResult;
import org.alexmond.unitrack.repository.ProjectMembershipRepository;
import org.alexmond.unitrack.repository.UserRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class GateFailureNotifierTest {

	private final NotificationService notifications = mock(NotificationService.class);

	private final ProjectMembershipRepository memberships = mock(ProjectMembershipRepository.class);

	private final UserRepository users = mock(UserRepository.class);

	private final ProjectSettingsService settings = mock(ProjectSettingsService.class);

	private final GateFailureNotifier notifier = new GateFailureNotifier(notifications, memberships, users, settings);

	private static QualityGateResult failed() {
		return new QualityGateResult(false, List.of(new RuleResult("min-coverage", false, "62% < 80%")));
	}

	private TestRun run(String branch) {
		Project project = mock(Project.class);
		given(project.getId()).willReturn(7L);
		given(project.getName()).willReturn("demo");
		TestRun run = mock(TestRun.class);
		given(run.getProject()).willReturn(project);
		given(run.getBranch()).willReturn(branch);
		return run;
	}

	private ProjectMembership member(long userId) {
		ProjectMembership m = mock(ProjectMembership.class);
		given(m.getUserId()).willReturn(userId);
		return m;
	}

	@Test
	void emailsMembersWithAnAddressWhenGateFailsOnWatchedBranch() {
		given(notifications.enabled()).willReturn(true);
		given(notifications.link(anyString())).willReturn("https://unitrack.example/runs/42");
		given(settings.gateConfig(7L)).willReturn(new GateConfig("main", 80.0, 5.0, true));
		TestRun run = run("main");
		given(run.getId()).willReturn(42L);
		given(run.getShortSha()).willReturn("abc1234");
		given(run.getTotalTests()).willReturn(10);
		given(run.getPassed()).willReturn(8);
		given(run.getFailed()).willReturn(1);
		given(run.getErrors()).willReturn(0);
		given(run.getSkipped()).willReturn(1);
		ProjectMembership m1 = member(1L);
		ProjectMembership m2 = member(2L);
		given(memberships.findByProjectIdOrderByRoleAscIdAsc(7L)).willReturn(List.of(m1, m2));
		given(users.findById(1L)).willReturn(Optional.of(new User("u1", "U1", "dev@example", "h", Role.USER)));
		given(users.findById(2L)).willReturn(Optional.of(new User("u2", "U2", null, "h", Role.USER)));

		notifier.notifyIfFailed(run, failed());

		verify(notifications, times(1)).send(eq("dev@example"), anyString(), anyString());
		verify(notifications, never()).send(eq(null), anyString(), anyString());
	}

	@Test
	void skipsWhenGatePassed() {
		QualityGateResult passed = new QualityGateResult(true, List.of());
		notifier.notifyIfFailed(run("main"), passed);
		verifyNoInteractions(memberships, users);
		verify(notifications, never()).send(anyString(), anyString(), anyString());
	}

	@Test
	void skipsWhenNotificationsDisabled() {
		given(notifications.enabled()).willReturn(false);
		notifier.notifyIfFailed(run("main"), failed());
		verifyNoInteractions(memberships, users);
	}

	@Test
	void skipsWhenRunIsNotOnWatchedBranch() {
		given(notifications.enabled()).willReturn(true);
		given(settings.gateConfig(7L)).willReturn(new GateConfig("main", 80.0, 5.0, true));
		notifier.notifyIfFailed(run("feature/x"), failed());
		verifyNoInteractions(memberships, users);
		verify(notifications, never()).send(anyString(), anyString(), anyString());
	}

	@Test
	void noEmailWhenProjectHasNoMembersWithAddresses() {
		given(notifications.enabled()).willReturn(true);
		given(settings.gateConfig(7L)).willReturn(new GateConfig("main", 80.0, 5.0, true));
		given(memberships.findByProjectIdOrderByRoleAscIdAsc(7L)).willReturn(List.of());
		notifier.notifyIfFailed(run("main"), failed());
		verify(notifications, never()).send(anyString(), anyString(), anyString());
	}

	@Test
	void swallowsErrorsFromDependencies() {
		given(notifications.enabled()).willReturn(true);
		given(settings.gateConfig(any())).willThrow(new RuntimeException("db down"));
		TestRun run = run("main");
		given(run.getId()).willReturn(42L);
		// must not throw
		notifier.notifyIfFailed(run, failed());
		verify(notifications, never()).send(anyString(), anyString(), anyString());
	}

}
