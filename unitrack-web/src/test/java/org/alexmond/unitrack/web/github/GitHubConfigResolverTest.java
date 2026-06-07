package org.alexmond.unitrack.web.github;

import java.util.Optional;

import org.alexmond.unitrack.domain.ProjectSettings;
import org.alexmond.unitrack.repository.ProjectSettingsRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.BDDMockito.given;

class GitHubConfigResolverTest {

	private GitHubProperties globals() {
		GitHubProperties p = new GitHubProperties();
		p.setEnabled(true);
		p.setContext("unitrack/quality-gate");
		p.setPrComment(true);
		return p;
	}

	@Test
	void fallsBackToGlobalsWhenNoOverride() {
		ProjectSettingsRepository repo = mock(ProjectSettingsRepository.class);
		given(repo.findByProjectId(1L)).willReturn(Optional.empty());
		GitHubConfigResolver.Effective cfg = new GitHubConfigResolver(globals(), repo).effective(1L);
		assertThat(cfg.enabled()).isTrue();
		assertThat(cfg.context()).isEqualTo("unitrack/quality-gate");
		assertThat(cfg.prComment()).isTrue();
	}

	@Test
	void appliesPerProjectOverrides() {
		ProjectSettings s = new ProjectSettings(1L);
		s.setGhEnabled(true);
		s.setGhContext("custom/ctx");
		s.setGhPrComment(false);
		ProjectSettingsRepository repo = mock(ProjectSettingsRepository.class);
		given(repo.findByProjectId(1L)).willReturn(Optional.of(s));

		GitHubConfigResolver.Effective cfg = new GitHubConfigResolver(globals(), repo).effective(1L);
		assertThat(cfg.context()).isEqualTo("custom/ctx");
		assertThat(cfg.prComment()).isFalse();
	}

	@Test
	void nullProjectIdUsesGlobals() {
		ProjectSettingsRepository repo = mock(ProjectSettingsRepository.class);
		GitHubConfigResolver.Effective cfg = new GitHubConfigResolver(globals(), repo).effective(null);
		assertThat(cfg.context()).isEqualTo("unitrack/quality-gate");
	}

}
