package org.alexmond.unitrack.web.github;

import java.util.List;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.QualityGateResult;
import org.alexmond.unitrack.repository.ProjectSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubPrCommentServiceTest {

	private GitHubProperties props() {
		GitHubProperties p = new GitHubProperties();
		p.setEnabled(true);
		p.setToken("secret");
		p.setApiUrl("https://api.github.com");
		p.setServerBaseUrl("https://unitrack.example");
		return p;
	}

	private TestRun run() {
		TestRun run = new TestRun(new Project("demo", "https://github.com/octo/repo"), "feature", "default", "abc123",
				null, null);
		run.applyTotals(3, 1, 0, 1, 100);
		run.setLineCoveragePct(80.0);
		return run;
	}

	/** Service whose resolver falls back to the global props (empty settings repo). */
	private GitHubPrCommentService svc(GitHubProperties props, RestClient.Builder builder) {
		return new GitHubPrCommentService(props, builder,
				new GitHubConfigResolver(props, mock(ProjectSettingsRepository.class)));
	}

	@Test
	void createsCommentWhenNoneExists() {
		GitHubProperties props = props();
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitHubPrCommentService service = svc(props, builder);

		server.expect(requestTo("https://api.github.com/repos/octo/repo/commits/abc123/pulls"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("[{\"number\":7}]", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://api.github.com/repos/octo/repo/issues/7/comments"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://api.github.com/repos/octo/repo/issues/7/comments"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("UniTrack")))
			.andRespond(withStatus(HttpStatus.CREATED));

		service.publish(run(), new QualityGateResult(false, List.of()), -1.5, 1, 2);
		server.verify();
	}

	@Test
	void updatesExistingCommentInPlace() {
		GitHubProperties props = props();
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitHubPrCommentService service = svc(props, builder);

		server.expect(requestTo("https://api.github.com/repos/octo/repo/commits/abc123/pulls"))
			.andRespond(withSuccess("[{\"number\":7}]", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://api.github.com/repos/octo/repo/issues/7/comments"))
			.andRespond(withSuccess("[{\"id\":42,\"body\":\"" + GitHubPrCommentService.MARKER + " old\"}]",
					MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://api.github.com/repos/octo/repo/issues/comments/42"))
			.andExpect(method(HttpMethod.PATCH))
			.andRespond(withSuccess());

		service.publish(run(), new QualityGateResult(true, List.of()), 1.0, 0, 0);
		server.verify();
	}

	@Test
	void skipsWhenNoPullRequest() {
		GitHubProperties props = props();
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitHubPrCommentService service = svc(props, builder);

		server.expect(requestTo("https://api.github.com/repos/octo/repo/commits/abc123/pulls"))
			.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		service.publish(run(), new QualityGateResult(true, List.of()), null, 0, 0);
		server.verify(); // no further calls
	}

	@Test
	void skipsWhenPrCommentDisabledForProject() {
		GitHubProperties props = props();
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitHubConfigResolver resolver = mock(GitHubConfigResolver.class);
		given(resolver.effective(any())).willReturn(new GitHubConfigResolver.Effective(true, "ctx", false));
		GitHubPrCommentService service = new GitHubPrCommentService(props, builder, resolver);

		// prComment overridden off for this project -> no GitHub calls at all.
		service.publish(run(), new QualityGateResult(true, List.of()), null, 0, 0);
		server.verify();
	}

	@Test
	void rendersTableWithMarkerAndMetrics() {
		String body = svc(props(), RestClient.builder()).render(run(), new QualityGateResult(false, List.of()), -1.5, 2,
				1);
		assertThat(body).startsWith(GitHubPrCommentService.MARKER);
		assertThat(body).contains("3 passed · 1 failed · 1 skipped (5 total)");
		assertThat(body).contains("80.0% (-1.5pp)");
		assertThat(body).contains("New failures | 2");
		assertThat(body).contains("Slower tests | 1");
		assertThat(body).contains("/runs/");
	}

}
