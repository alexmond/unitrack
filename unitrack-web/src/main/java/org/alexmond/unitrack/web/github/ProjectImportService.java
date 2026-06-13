package org.alexmond.unitrack.web.github;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Provisions UniTrack {@link Project}s from selected GitHub repositories. */
@Service
@RequiredArgsConstructor
public class ProjectImportService {

	private final ProjectRepository projects;

	/** Names of projects that already exist (so the picker can mark them as imported). */
	@Transactional(readOnly = true)
	public Set<String> existingProjectNames() {
		return projects.findAllByOrderByNameAsc().stream().map(Project::getName).collect(Collectors.toSet());
	}

	/**
	 * Creates a project for each selected repo (matched by full name against
	 * {@code available} so repo URLs come from GitHub, never the client). Repos whose
	 * project name already exists are skipped.
	 */
	@Transactional
	public Result importRepos(List<GitHubRepo> available, Collection<String> selectedFullNames) {
		Set<String> wanted = new HashSet<>(selectedFullNames);
		List<String> imported = new ArrayList<>();
		List<String> skipped = new ArrayList<>();
		for (GitHubRepo repo : available) {
			if (!wanted.contains(repo.fullName())) {
				continue;
			}
			String name = repo.name();
			if (projects.findByName(name).isPresent()) {
				skipped.add(name);
				continue;
			}
			projects.save(new Project(name, repo.htmlUrl()));
			imported.add(name);
		}
		return new Result(imported, skipped);
	}

	/**
	 * Outcome of an import: project names created vs. skipped because they already exist.
	 */
	public record Result(List<String> imported, List<String> skipped) {
	}

}
