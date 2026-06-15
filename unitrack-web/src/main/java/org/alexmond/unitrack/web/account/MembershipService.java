package org.alexmond.unitrack.web.account;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.ProjectMembership;
import org.alexmond.unitrack.domain.ProjectRole;
import org.alexmond.unitrack.domain.Role;
import org.alexmond.unitrack.domain.User;
import org.alexmond.unitrack.domain.Visibility;
import org.alexmond.unitrack.repository.ProjectMembershipRepository;
import org.alexmond.unitrack.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project membership management and the authorization checks built on it. Global
 * {@link Role#ADMIN} users implicitly have OWNER rights on every project.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipService {

	private final UserRepository users;

	private final ProjectMembershipRepository memberships;

	/** Members of a project, with usernames resolved, for the admin page. */
	public List<MemberView> members(Long projectId) {
		return this.memberships.findByProjectIdOrderByRoleAscIdAsc(projectId)
			.stream()
			.map((m) -> new MemberView(m.getId(), username(m.getUserId()), m.getRole()))
			.toList();
	}

	/** Adds the user to the project (or updates their role if already a member). */
	@Transactional
	public void addOrUpdate(Long projectId, String username, ProjectRole role) {
		User user = this.users.findByUsername(username)
			.orElseThrow(() -> new IllegalArgumentException("No such user: " + username));
		ProjectMembership membership = this.memberships.findByProjectIdAndUserId(projectId, user.getId())
			.orElseGet(() -> new ProjectMembership(projectId, user.getId(), role));
		membership.setRole(role);
		this.memberships.save(membership);
	}

	/** Grants a role only if the username maps to a real user; otherwise a no-op. */
	@Transactional
	public void grantIfUserExists(Long projectId, String username, ProjectRole role) {
		if (username != null && this.users.findByUsername(username).isPresent()) {
			addOrUpdate(projectId, username, role);
		}
	}

	@Transactional
	public void remove(Long membershipId) {
		this.memberships.deleteById(membershipId);
	}

	/** Whether the user may write to the project (settings, triage, …). */
	public boolean canWrite(String username, Long projectId) {
		return hasRole(username, projectId, ProjectRole.WRITE);
	}

	/** Whether the user may manage the project's members (owner or admin). */
	public boolean canManage(String username, Long projectId) {
		return hasRole(username, projectId, ProjectRole.OWNER);
	}

	/**
	 * Whether the user (may be null/anonymous) may read the project. PUBLIC projects are
	 * readable by everyone; PRIVATE projects only by members (READ+) and admins.
	 */
	public boolean canRead(String username, Project project) {
		if (project.getVisibility() == Visibility.PUBLIC) {
			return true;
		}
		return username != null && hasRole(username, project.getId(), ProjectRole.READ);
	}

	/** Filters a list of projects down to the ones the user may read. */
	public List<Project> readable(String username, List<Project> projects) {
		return projects.stream().filter((p) -> canRead(username, p)).toList();
	}

	private boolean hasRole(String username, Long projectId, ProjectRole minimum) {
		User user = this.users.findByUsername(username).orElse(null);
		if (user == null) {
			return false;
		}
		if (user.getRole() == Role.ADMIN) {
			return true;
		}
		return this.memberships.findByProjectIdAndUserId(projectId, user.getId())
			.map(ProjectMembership::getRole)
			.map((role) -> covers(role, minimum))
			.orElse(false);
	}

	/** OWNER covers WRITE and READ; WRITE covers READ. */
	private static boolean covers(ProjectRole held, ProjectRole minimum) {
		return held.ordinal() <= minimum.ordinal();
	}

	private String username(Long userId) {
		return this.users.findById(userId).map(User::getUsername).orElse("?");
	}

	/** A project member for the admin page. */
	public record MemberView(Long id, String username, ProjectRole role) {
	}

}
