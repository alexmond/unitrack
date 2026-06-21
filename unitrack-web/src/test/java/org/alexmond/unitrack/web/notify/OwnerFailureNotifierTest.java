package org.alexmond.unitrack.web.notify;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.User;
import org.alexmond.unitrack.report.GateConfig;
import org.alexmond.unitrack.report.OwnershipService;
import org.alexmond.unitrack.report.ProjectSettingsService;
import org.alexmond.unitrack.report.TestRegressionResult;
import org.alexmond.unitrack.report.TestRegressionResult.RegressedTest;
import org.alexmond.unitrack.report.TestRegressionService;
import org.alexmond.unitrack.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Owner-routed failure notifications (#225): each owner's newly-failing tests are emailed
 * to a resolvable recipient (a UniTrack user, honoring their preference), and unroutable
 * owners are skipped.
 */
@ExtendWith(MockitoExtension.class)
class OwnerFailureNotifierTest {

	@Mock
	private NotificationService notifications;

	@Mock
	private OwnershipService ownership;

	@Mock
	private TestRegressionService regression;

	@Mock
	private ProjectSettingsService settings;

	@Mock
	private UserRepository users;

	@InjectMocks
	private OwnerFailureNotifier notifier;

	@Test
	void emailsResolvableOwnersAndSkipsUnroutable() {
		given(this.notifications.enabled()).willReturn(true);
		given(this.notifications.link(org.mockito.ArgumentMatchers.anyString())).willReturn("http://x/runs/5");

		Project project = org.mockito.Mockito.mock(Project.class);
		given(project.getId()).willReturn(10L);
		given(project.getName()).willReturn("alpha");
		TestRun run = org.mockito.Mockito.mock(TestRun.class);
		given(run.getProject()).willReturn(project);
		given(run.getBranch()).willReturn("main");
		given(run.getId()).willReturn(5L);

		given(this.settings.gateConfig(10L)).willReturn(new GateConfig("main", null, 0.0, true));
		TestRegressionResult diff = new TestRegressionResult(true, 1L, "main",
				List.of(new RegressedTest("com.x.A", "t1", null, null)), List.of(), List.of());
		given(this.regression.diff(5L)).willReturn(Optional.of(diff));

		Map<String, List<String>> byOwner = new LinkedHashMap<>();
		byOwner.put("@alice", List.of("com.x.A.t1")); // resolves to a user
		byOwner.put("team-x", List.of("com.x.B.t2")); // no user, not an email →
														// unroutable
		given(this.ownership.failuresByOwner(eq(10L), anyList())).willReturn(byOwner);

		User alice = org.mockito.Mockito.mock(User.class);
		given(alice.isNotifyGateFailure()).willReturn(true);
		given(alice.getEmail()).willReturn("alice@corp.com");
		given(this.users.findByUsername("alice")).willReturn(Optional.of(alice));
		given(this.users.findByUsername("team-x")).willReturn(Optional.empty());

		this.notifier.notifyOwners(run);

		verify(this.notifications).send(eq("alice@corp.com"), contains("newly failed"), contains("com.x.A.t1"));
		verify(this.notifications, never()).send(eq("team-x"), org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString());
	}

}
