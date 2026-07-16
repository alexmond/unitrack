package org.alexmond.unitrack.web.gitlab;

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

class GitLabRepoServiceTest {

	private GitLabProperties props() {
		GitLabProperties p = new GitLabProperties();
		p.setEnabled(true);
		p.setToken("secret");
		p.setApiUrl("https://gitlab.com/api/v4");
		return p;
	}

	@Test
	void listsAndMapsProjectsFromTheApi() {
		GitLabProperties props = props();
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitLabRepoService service = new GitLabRepoService(props, builder);

		String json = """
				[
				  {"name":"proj-a","path_with_namespace":"group/proj-a","web_url":"https://gitlab.com/group/proj-a",
				   "default_branch":"main","visibility":"public","description":"first"},
				  {"name":"proj-b","path_with_namespace":"group/sub/proj-b","web_url":"https://gitlab.com/group/sub/proj-b",
				   "default_branch":"develop","visibility":"private","description":null}
				]
				""";
		server
			.expect(requestTo("https://gitlab.com/api/v4/projects?membership=true&order_by=last_activity_at"
					+ "&sort=desc&per_page=100"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header("PRIVATE-TOKEN", "secret"))
			.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

		GitLabRepoService.RepoList result = service.listRepos();
		server.verify();

		assertThat(result.truncated()).isFalse();
		assertThat(result.repos()).hasSize(2);
		assertThat(result.repos().get(0).fullName()).isEqualTo("group/proj-a");
		assertThat(result.repos().get(0).webUrl()).isEqualTo("https://gitlab.com/group/proj-a");
		assertThat(result.repos().get(0).isPrivate()).isFalse();
		assertThat(result.repos().get(1).fullName()).isEqualTo("group/sub/proj-b");
		assertThat(result.repos().get(1).defaultBranch()).isEqualTo("develop");
		assertThat(result.repos().get(1).isPrivate()).isTrue();
	}

	@Test
	void followsLinkHeaderPaginationAcrossPages() {
		GitLabProperties props = props();
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitLabRepoService service = new GitLabRepoService(props, builder);

		String page1 = "[{\"name\":\"a\",\"path_with_namespace\":\"group/a\",\"web_url\":\"https://gitlab.com/group/a\","
				+ "\"default_branch\":\"main\",\"visibility\":\"public\",\"description\":null}]";
		String page2 = "[{\"name\":\"b\",\"path_with_namespace\":\"group/b\",\"web_url\":\"https://gitlab.com/group/b\","
				+ "\"default_branch\":\"main\",\"visibility\":\"public\",\"description\":null}]";

		HttpHeaders linkToNext = new HttpHeaders();
		linkToNext.add(HttpHeaders.LINK, "<https://gitlab.com/api/v4/projects?page=2>; rel=\"next\"");

		server
			.expect(requestTo("https://gitlab.com/api/v4/projects?membership=true&order_by=last_activity_at"
					+ "&sort=desc&per_page=100"))
			.andRespond(withSuccess(page1, MediaType.APPLICATION_JSON).headers(linkToNext));
		server.expect(requestTo("https://gitlab.com/api/v4/projects?page=2"))
			.andRespond(withSuccess(page2, MediaType.APPLICATION_JSON));

		GitLabRepoService.RepoList result = service.listRepos();
		server.verify();

		assertThat(result.truncated()).isFalse();
		assertThat(result.repos()).extracting(GitLabRepo::fullName).containsExactly("group/a", "group/b");
	}

	@Test
	void notConfiguredWhenDisabledOrTokenMissing() {
		GitLabProperties props = props();
		GitLabRepoService service = new GitLabRepoService(props, RestClient.builder());
		assertThat(service.isConfigured()).isTrue();

		props.setEnabled(false);
		assertThat(service.isConfigured()).isFalse();

		props.setEnabled(true);
		props.setToken("  ");
		assertThat(service.isConfigured()).isFalse();
	}

}
