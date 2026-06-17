package org.alexmond.unitrack.web.gitlab;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.ProjectSettings;
import org.alexmond.unitrack.repository.ProjectSettingsRepository;
import org.springframework.stereotype.Component;

/**
 * Resolves whether GitLab posting is enabled for a project: the per-project
 * {@code glEnabled} override merged over the global {@link GitLabProperties}. The token
 * is always global.
 */
@Component
@RequiredArgsConstructor
public class GitLabConfigResolver {

	private final GitLabProperties props;

	private final ProjectSettingsRepository settings;

	public boolean enabled(Long projectId) {
		ProjectSettings s = (projectId != null) ? this.settings.findByProjectId(projectId).orElse(null) : null;
		return (s != null && s.getGlEnabled() != null) ? s.getGlEnabled() : this.props.isEnabled();
	}

}
