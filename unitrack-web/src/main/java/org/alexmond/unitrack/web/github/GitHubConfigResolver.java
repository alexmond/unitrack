package org.alexmond.unitrack.web.github;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.ProjectSettings;
import org.alexmond.unitrack.repository.ProjectSettingsRepository;
import org.springframework.stereotype.Component;

/**
 * Resolves the effective GitHub integration config for a project: per-project overrides
 * (enabled / status context / PR comment) merged over the global
 * {@link GitHubProperties}. The token is always global and never overridden per project.
 */
@Component
@RequiredArgsConstructor
public class GitHubConfigResolver {

	private final GitHubProperties props;

	private final ProjectSettingsRepository settings;

	public Effective effective(Long projectId) {
		ProjectSettings s = (projectId != null) ? this.settings.findByProjectId(projectId).orElse(null) : null;
		boolean enabled = (s != null && s.getGhEnabled() != null) ? s.getGhEnabled() : this.props.isEnabled();
		String context = (s != null && s.getGhContext() != null) ? s.getGhContext() : this.props.getContext();
		boolean prComment = (s != null && s.getGhPrComment() != null) ? s.getGhPrComment() : this.props.isPrComment();
		return new Effective(enabled, context, prComment);
	}

	/** Effective GitHub config for one project. */
	public record Effective(boolean enabled, String context, boolean prComment) {
	}

}
