package org.alexmond.unitrack.web.gitlab;

import java.util.List;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.QualityGateResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitLabServiceTest {

	private GitLabProperties props(boolean enabled) {
		GitLabProperties p = new GitLabProperties();
		p.setEnabled(enabled);
		p.setToken("secret");
		p.setServerBaseUrl("https://unitrack.example");
		return p;
	}

	/** Resolver over an empty settings repo → always falls back to the global props. */
	private GitLabConfigResolver resolver(GitLabProperties props) {
		return new GitLabConfigResolver(props,
				org.mockito.Mockito.mock(org.alexmond.unitrack.repository.ProjectSettingsRepository.class));
	}

	private TestRun run(String repoUrl) {
		TestRun run = new TestRun(new Project("demo", repoUrl), "main", "default", "abc123", null, null);
		run.applyTotals(3, 1, 0, 0, 100);
		run.setLineCoveragePct(80.0);
		return run;
	}

	@Test
	void postsCommitStatusForFailingGate() {
		GitLabProperties props = props(true);
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitLabService service = new GitLabService(props, resolver(props), builder);

		server.expect(requestTo("https://gitlab.com/api/v4/projects/octo%2Frepo/statuses/abc123"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("PRIVATE-TOKEN", "secret"))
			.andExpect(jsonPath("$.state").value("failed"))
			.andExpect(jsonPath("$.name").value("unitrack/quality-gate"))
			.andExpect(jsonPath("$.coverage").value(80.0))
			.andRespond(withStatus(HttpStatus.CREATED));

		service.publishStatus(run("https://gitlab.com/octo/repo"), new QualityGateResult(false, List.of()), 1.5);
		server.verify();
	}

	@Test
	void createsMergeRequestNoteWhenNoneExists() {
		GitLabProperties props = props(true);
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitLabService service = new GitLabService(props, resolver(props), builder);
		TestRun run = run("https://gitlab.com/octo/repo");
		run.setPrNumber(7);

		// Resolve token identity, then list notes: none marked → create.
		server.expect(requestTo("https://gitlab.com/api/v4/user"))
			.andRespond(withSuccess("{\"id\":100}", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://gitlab.com/api/v4/projects/octo%2Frepo/merge_requests/7/notes?per_page=100"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://gitlab.com/api/v4/projects/octo%2Frepo/merge_requests/7/notes"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.body").exists())
			.andRespond(withStatus(HttpStatus.CREATED));

		service.publishMrNote(run, new QualityGateResult(true, List.of()), null, 2);
		server.verify();
	}

	@Test
	void updatesExistingMergeRequestNoteInPlace() {
		GitLabProperties props = props(true);
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitLabService service = new GitLabService(props, resolver(props), builder);
		TestRun run = run("https://gitlab.com/octo/repo");
		run.setPrNumber(7);

		// Our own prior marked note (author id matches /user) → update it (PUT).
		server.expect(requestTo("https://gitlab.com/api/v4/user"))
			.andRespond(withSuccess("{\"id\":100}", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://gitlab.com/api/v4/projects/octo%2Frepo/merge_requests/7/notes?per_page=100"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(
					"[{\"id\":42,\"author\":{\"id\":100},\"body\":\"" + GitLabService.MR_NOTE_MARKER + " old\"}]",
					MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://gitlab.com/api/v4/projects/octo%2Frepo/merge_requests/7/notes/42"))
			.andExpect(method(HttpMethod.PUT))
			.andExpect(jsonPath("$.body").exists())
			.andRespond(withSuccess());

		service.publishMrNote(run, new QualityGateResult(true, List.of()), null, 2);
		server.verify();
	}

	@Test
	void ignoresMarkedNoteAuthoredByAnotherUser() {
		GitLabProperties props = props(true);
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitLabService service = new GitLabService(props, resolver(props), builder);
		TestRun run = run("https://gitlab.com/octo/repo");
		run.setPrNumber(7);

		// A third party planted the marker (author 999 != our 100) → don't hijack it;
		// create
		// our own note instead.
		server.expect(requestTo("https://gitlab.com/api/v4/user"))
			.andRespond(withSuccess("{\"id\":100}", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://gitlab.com/api/v4/projects/octo%2Frepo/merge_requests/7/notes?per_page=100"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(
					"[{\"id\":77,\"author\":{\"id\":999},\"body\":\"" + GitLabService.MR_NOTE_MARKER + " planted\"}]",
					MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://gitlab.com/api/v4/projects/octo%2Frepo/merge_requests/7/notes"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withStatus(HttpStatus.CREATED));

		service.publishMrNote(run, new QualityGateResult(true, List.of()), null, 2);
		server.verify();
	}

	@Test
	void doesNothingWhenDisabledOrNotGitlab() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		// Disabled → no request.
		new GitLabService(props(false), resolver(props(false)), builder)
			.publishStatus(run("https://gitlab.com/octo/repo"), new QualityGateResult(true, List.of()), null);
		// Enabled but a GitHub repo → no request.
		new GitLabService(props(true), resolver(props(true)), builder)
			.publishStatus(run("https://github.com/octo/repo"), new QualityGateResult(true, List.of()), null);
		server.verify();
	}

	@Test
	void parsesProjectPathScopedToConfiguredHost() {
		assertThat(GitLabService.projectPath("https://gitlab.com/group/proj", "gitlab.com")).isEqualTo("group/proj");
		assertThat(GitLabService.projectPath("https://gitlab.com/group/sub/proj.git", "gitlab.com"))
			.isEqualTo("group/sub/proj");
		assertThat(GitLabService.projectPath("git@gitlab.com:group/proj.git", "gitlab.com")).isEqualTo("group/proj");
		// A leading userinfo (e.g. a token user) is stripped before the host is checked.
		assertThat(GitLabService.projectPath("https://user@gitlab.com/group/proj.git", "gitlab.com"))
			.isEqualTo("group/proj");
		// Self-hosted: recognised only when the configured host matches it.
		assertThat(GitLabService.projectPath("https://gitlab.example.com/team/app", "gitlab.example.com"))
			.isEqualTo("team/app");
		assertThat(GitLabService.projectPath("https://gitlab.example.com/team/app", "gitlab.com")).isNull();
		// Non-GitLab host rejected even though it once matched the "gitlab" substring
		// test.
		assertThat(GitLabService.projectPath("https://github.com/octo/repo", "gitlab.com")).isNull();
		assertThat(GitLabService.projectPath(null, "gitlab.com")).isNull();
	}

	@Test
	void derivesHostFromApiUrl() {
		assertThat(GitLabService.hostOf("https://gitlab.com/api/v4")).isEqualTo("gitlab.com");
		assertThat(GitLabService.hostOf("https://gitlab.example.com/api/v4")).isEqualTo("gitlab.example.com");
		assertThat(GitLabService.hostOf("https://gitlab.example.com:8443/api/v4")).isEqualTo("gitlab.example.com");
		assertThat(GitLabService.hostOf(null)).isNull();
	}

}
