package org.alexmond.unitrack.web.gitlab;

import java.util.Optional;

import org.alexmond.unitrack.domain.ProjectSettings;
import org.alexmond.unitrack.repository.ProjectSettingsRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GitLabConfigResolverTest {

	private GitLabProperties globals(boolean enabled) {
		GitLabProperties p = new GitLabProperties();
		p.setEnabled(enabled);
		return p;
	}

	@Test
	void fallsBackToGlobalWhenNoOverride() {
		ProjectSettingsRepository repo = mock(ProjectSettingsRepository.class);
		given(repo.findByProjectId(1L)).willReturn(Optional.empty());
		assertThat(new GitLabConfigResolver(globals(true), repo).enabled(1L)).isTrue();
		assertThat(new GitLabConfigResolver(globals(false), repo).enabled(1L)).isFalse();
	}

	@Test
	void perProjectOverrideWins() {
		ProjectSettings off = new ProjectSettings(1L);
		off.setGlEnabled(false);
		ProjectSettingsRepository repo = mock(ProjectSettingsRepository.class);
		given(repo.findByProjectId(1L)).willReturn(Optional.of(off));
		// Global on, but the project overrides to off.
		assertThat(new GitLabConfigResolver(globals(true), repo).enabled(1L)).isFalse();
	}

}
