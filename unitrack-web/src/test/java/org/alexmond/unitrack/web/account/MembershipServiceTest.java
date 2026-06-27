package org.alexmond.unitrack.web.account;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.ProjectMembership;
import org.alexmond.unitrack.domain.Role;
import org.alexmond.unitrack.domain.User;
import org.alexmond.unitrack.domain.Visibility;
import org.alexmond.unitrack.repository.ProjectMembershipRepository;
import org.alexmond.unitrack.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MembershipServiceTest {

	@Mock
	private UserRepository users;

	@Mock
	private ProjectMembershipRepository memberships;

	@InjectMocks
	private MembershipService service;

	private static Project project(long id, Visibility visibility) {
		Project p = mock(Project.class);
		given(p.getId()).willReturn(id);
		given(p.getVisibility()).willReturn(visibility);
		return p;
	}

	private static ProjectMembership memberOf(long projectId) {
		ProjectMembership m = mock(ProjectMembership.class);
		given(m.getProjectId()).willReturn(projectId);
		return m;
	}

	@Test
	void readableBy_loadsUserAndMembershipsOnce_noPerProjectQuery() {
		User bob = mock(User.class);
		given(bob.getId()).willReturn(7L);
		given(bob.getRole()).willReturn(Role.USER);
		// build the membership list first — memberOf() stubs internally, so it can't run
		// nested
		// inside another given(...) argument (UnfinishedStubbing).
		List<ProjectMembership> bobMemberships = List.of(memberOf(10L), memberOf(20L));
		given(this.users.findByUsername("bob")).willReturn(Optional.of(bob));
		given(this.memberships.findByUserId(7L)).willReturn(bobMemberships);

		Predicate<Project> canRead = this.service.readableBy("bob");

		// public project: readable by anyone
		assertThat(canRead.test(project(1L, Visibility.PUBLIC))).isTrue();
		// private project the user is a member of
		assertThat(canRead.test(project(10L, Visibility.PRIVATE))).isTrue();
		// private project the user is NOT a member of
		assertThat(canRead.test(project(99L, Visibility.PRIVATE))).isFalse();

		// The whole point of the fix: ONE user lookup + ONE membership lookup for the
		// entire
		// list, never a per-project membership query (the old board N+1).
		then(this.users).should(times(1)).findByUsername("bob");
		then(this.memberships).should(times(1)).findByUserId(7L);
		then(this.memberships).should(never()).findByProjectIdAndUserId(anyLong(), anyLong());
	}

	@Test
	void readableBy_anonymousSeesOnlyPublic() {
		Predicate<Project> canRead = this.service.readableBy(null);

		assertThat(canRead.test(project(1L, Visibility.PUBLIC))).isTrue();
		assertThat(canRead.test(project(2L, Visibility.PRIVATE))).isFalse();
		then(this.users).should(never()).findByUsername(any());
	}

	@Test
	void readableBy_adminSeesEverything_withoutLoadingMemberships() {
		User admin = mock(User.class);
		given(admin.getRole()).willReturn(Role.ADMIN);
		given(this.users.findByUsername("root")).willReturn(Optional.of(admin));

		Predicate<Project> canRead = this.service.readableBy("root");

		assertThat(canRead.test(project(1L, Visibility.PRIVATE))).isTrue();
		then(this.memberships).should(never()).findByUserId(anyLong());
	}

}
