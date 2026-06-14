package org.alexmond.unitrack.web.github;

import java.util.List;
import java.util.Optional;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ProjectImportServiceTest {

	private final ProjectRepository projects = mock(ProjectRepository.class);

	private final ProjectImportService service = new ProjectImportService(projects);

	private GitHubRepo repo(String fullName, String name) {
		return new GitHubRepo(name, fullName, "https://github.com/" + fullName, "main", false, null);
	}

	@Test
	void importsSelectedNewReposAndSkipsExisting() {
		GitHubRepo a = repo("octo/repo-a", "repo-a");
		GitHubRepo b = repo("octo/repo-b", "repo-b");
		GitHubRepo c = repo("octo/repo-c", "repo-c");

		// repo-b already exists; a and c are new.
		given(projects.findByName("repo-a")).willReturn(Optional.empty());
		given(projects.findByName("repo-b")).willReturn(Optional.of(new Project("repo-b", null)));
		given(projects.findByName("repo-c")).willReturn(Optional.empty());

		ProjectImportService.Result result = service.importRepos(List.of(a, b, c),
				List.of("octo/repo-a", "octo/repo-b", "octo/repo-c"));

		assertThat(result.imported()).containsExactlyInAnyOrder("repo-a", "repo-c");
		assertThat(result.skipped()).containsExactly("repo-b");
		verify(projects, never()).save(org.mockito.ArgumentMatchers.argThat((p) -> "repo-b".equals(p.getName())));
		verify(projects).save(org.mockito.ArgumentMatchers
			.argThat((p) -> "repo-a".equals(p.getName()) && "https://github.com/octo/repo-a".equals(p.getRepoUrl())));
	}

	@Test
	void ignoresReposThatWereNotSelected() {
		GitHubRepo a = repo("octo/repo-a", "repo-a");
		GitHubRepo b = repo("octo/repo-b", "repo-b");
		given(projects.findByName(any())).willReturn(Optional.empty());

		ProjectImportService.Result result = service.importRepos(List.of(a, b), List.of("octo/repo-a"));

		assertThat(result.imported()).containsExactly("repo-a");
		verify(projects).save(any());
	}

}
