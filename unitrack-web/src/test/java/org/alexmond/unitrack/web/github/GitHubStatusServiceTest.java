package org.alexmond.unitrack.web.github;

import java.util.List;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.QualityGateResult;
import org.alexmond.unitrack.repository.ProjectSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GitHubStatusServiceTest {

	private GitHubProperties props(boolean enabled) {
		GitHubProperties p = new GitHubProperties();
		p.setEnabled(enabled);
		p.setToken("secret");
		p.setApiUrl("https://api.github.com");
		p.setServerBaseUrl("https://unitrack.example");
		return p;
	}

	/**
	 * Resolver backed by an empty settings repo -> always falls back to the global props.
	 */
	private GitHubConfigResolver resolver(GitHubProperties props) {
		return new GitHubConfigResolver(props, mock(ProjectSettingsRepository.class));
	}

	private TestRun run() {
		TestRun run = new TestRun(new Project("demo", "https://github.com/octo/repo"), "main", "default", "abc123",
				null, null);
		run.applyTotals(3, 1, 0, 0, 100);
		run.setLineCoveragePct(80.0);
		return run;
	}

	@Test
	void postsCommitStatusForFailingGate() {
		GitHubProperties props = props(true);
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitHubStatusService service = new GitHubStatusService(props, builder, resolver(props),
				GitHubAuth.patOnly(props));

		server.expect(requestTo("https://api.github.com/repos/octo/repo/statuses/abc123"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", "Bearer secret"))
			.andExpect(jsonPath("$.state").value("failure"))
			.andExpect(jsonPath("$.context").value("unitrack/quality-gate"))
			.andExpect(jsonPath("$.target_url").value("https://unitrack.example/runs/null"))
			.andRespond(withStatus(HttpStatus.CREATED));

		service.publish(run(), new QualityGateResult(false, List.of()), 1.5);
		server.verify();
	}

	@Test
	void doesNothingWhenDisabled() {
		GitHubProperties props = props(false);
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitHubStatusService service = new GitHubStatusService(props, builder, resolver(props),
				GitHubAuth.patOnly(props));

		// No request expected.
		service.publish(run(), new QualityGateResult(true, List.of()), null);
		server.verify();
	}

	@Test
	void usesPerProjectContextOverride() {
		GitHubProperties props = props(true);
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitHubConfigResolver resolver = mock(GitHubConfigResolver.class);
		given(resolver.effective(any())).willReturn(new GitHubConfigResolver.Effective(true, "custom/ctx", true));
		GitHubStatusService service = new GitHubStatusService(props, builder, resolver, GitHubAuth.patOnly(props));

		server.expect(requestTo("https://api.github.com/repos/octo/repo/statuses/abc123"))
			.andExpect(jsonPath("$.context").value("custom/ctx"))
			.andRespond(withStatus(HttpStatus.CREATED));

		service.publish(run(), new QualityGateResult(true, List.of()), null);
		server.verify();
	}

	@Test
	void parsesOwnerAndRepoFromVariousUrls() {
		assertThat(GitHubStatusService.parseOwnerRepo("https://github.com/octo/repo")).containsExactly("octo", "repo");
		assertThat(GitHubStatusService.parseOwnerRepo("https://github.com/octo/repo.git")).containsExactly("octo",
				"repo");
		assertThat(GitHubStatusService.parseOwnerRepo("git@github.com:octo/repo.git")).containsExactly("octo", "repo");
		assertThat(GitHubStatusService.parseOwnerRepo("https://gitlab.com/octo/repo")).isNull();
		assertThat(GitHubStatusService.parseOwnerRepo(null)).isNull();
	}

}
