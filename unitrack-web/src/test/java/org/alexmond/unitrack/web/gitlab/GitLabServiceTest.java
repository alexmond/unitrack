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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GitLabServiceTest {

	private GitLabProperties props(boolean enabled) {
		GitLabProperties p = new GitLabProperties();
		p.setEnabled(enabled);
		p.setToken("secret");
		p.setServerBaseUrl("https://unitrack.example");
		return p;
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
		GitLabService service = new GitLabService(props, builder);

		server.expect(requestTo("https://gitlab.com/api/v4/projects/octo%2Frepo/statuses/abc123"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("PRIVATE-TOKEN", "secret"))
			.andExpect(jsonPath("$.state").value("failed"))
			.andExpect(jsonPath("$.name").value("unitrack/quality-gate"))
			.andRespond(withStatus(HttpStatus.CREATED));

		service.publishStatus(run("https://gitlab.com/octo/repo"), new QualityGateResult(false, List.of()), 1.5);
		server.verify();
	}

	@Test
	void postsMergeRequestNoteWhenPrPresent() {
		GitLabProperties props = props(true);
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitLabService service = new GitLabService(props, builder);
		TestRun run = run("https://gitlab.com/octo/repo");
		run.setPrNumber(7);

		server.expect(requestTo("https://gitlab.com/api/v4/projects/octo%2Frepo/merge_requests/7/notes"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.body").exists())
			.andRespond(withStatus(HttpStatus.CREATED));

		service.publishMrNote(run, new QualityGateResult(true, List.of()), null, 2);
		server.verify();
	}

	@Test
	void doesNothingWhenDisabledOrNotGitlab() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		// Disabled → no request.
		new GitLabService(props(false), builder).publishStatus(run("https://gitlab.com/octo/repo"),
				new QualityGateResult(true, List.of()), null);
		// Enabled but a GitHub repo → no request.
		new GitLabService(props(true), builder).publishStatus(run("https://github.com/octo/repo"),
				new QualityGateResult(true, List.of()), null);
		server.verify();
	}

	@Test
	void parsesProjectPathFromVariousUrls() {
		assertThat(GitLabService.projectPath("https://gitlab.com/group/proj")).isEqualTo("group/proj");
		assertThat(GitLabService.projectPath("https://gitlab.com/group/sub/proj.git")).isEqualTo("group/sub/proj");
		assertThat(GitLabService.projectPath("git@gitlab.com:group/proj.git")).isEqualTo("group/proj");
		assertThat(GitLabService.projectPath("https://gitlab.example.com/team/app")).isEqualTo("team/app");
		assertThat(GitLabService.projectPath("https://github.com/octo/repo")).isNull();
		assertThat(GitLabService.projectPath(null)).isNull();
	}

}
