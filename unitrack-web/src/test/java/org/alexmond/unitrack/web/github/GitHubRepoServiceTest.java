package org.alexmond.unitrack.web.github;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubRepoServiceTest {

	private GitHubProperties props() {
		GitHubProperties p = new GitHubProperties();
		p.setEnabled(true);
		p.setToken("secret");
		p.setApiUrl("https://api.github.com");
		return p;
	}

	@Test
	void listsAndMapsReposFromTheApi() {
		GitHubProperties props = props();
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitHubRepoService service = new GitHubRepoService(props, builder);

		String json = """
				[
				  {"name":"repo-a","full_name":"octo/repo-a","html_url":"https://github.com/octo/repo-a",
				   "default_branch":"main","private":false,"description":"first"},
				  {"name":"repo-b","full_name":"octo/repo-b","html_url":"https://github.com/octo/repo-b",
				   "default_branch":"develop","private":true,"description":null}
				]
				""";
		server.expect(requestTo("https://api.github.com/user/repos?per_page=100&sort=updated"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header("Authorization", "Bearer secret"))
			.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

		GitHubRepoService.RepoList result = service.listRepos();
		server.verify();

		assertThat(result.truncated()).isFalse();
		assertThat(result.repos()).hasSize(2);
		assertThat(result.repos().get(0).fullName()).isEqualTo("octo/repo-a");
		assertThat(result.repos().get(0).htmlUrl()).isEqualTo("https://github.com/octo/repo-a");
		assertThat(result.repos().get(0).isPrivate()).isFalse();
		assertThat(result.repos().get(1).defaultBranch()).isEqualTo("develop");
		assertThat(result.repos().get(1).isPrivate()).isTrue();
	}

	@Test
	void followsLinkHeaderPaginationAcrossPages() {
		GitHubProperties props = props();
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitHubRepoService service = new GitHubRepoService(props, builder);

		String page1 = "[{\"name\":\"a\",\"full_name\":\"octo/a\",\"html_url\":\"https://github.com/octo/a\","
				+ "\"default_branch\":\"main\",\"private\":false,\"description\":null}]";
		String page2 = "[{\"name\":\"b\",\"full_name\":\"octo/b\",\"html_url\":\"https://github.com/octo/b\","
				+ "\"default_branch\":\"main\",\"private\":false,\"description\":null}]";

		HttpHeaders linkToNext = new HttpHeaders();
		linkToNext.add(HttpHeaders.LINK, "<https://api.github.com/user/repos?page=2>; rel=\"next\"");

		server.expect(requestTo("https://api.github.com/user/repos?per_page=100&sort=updated"))
			.andRespond(withSuccess(page1, MediaType.APPLICATION_JSON).headers(linkToNext));
		server.expect(requestTo("https://api.github.com/user/repos?page=2"))
			.andRespond(withSuccess(page2, MediaType.APPLICATION_JSON));

		GitHubRepoService.RepoList result = service.listRepos();
		server.verify();

		assertThat(result.truncated()).isFalse();
		assertThat(result.repos()).extracting(GitHubRepo::fullName).containsExactly("octo/a", "octo/b");
	}

	@Test
	void notConfiguredWhenDisabledOrTokenMissing() {
		GitHubProperties props = props();
		GitHubRepoService service = new GitHubRepoService(props, RestClient.builder());
		assertThat(service.isConfigured()).isTrue();

		props.setEnabled(false);
		assertThat(service.isConfigured()).isFalse();

		props.setEnabled(true);
		props.setToken("  ");
		assertThat(service.isConfigured()).isFalse();
	}

}
