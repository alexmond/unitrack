package org.alexmond.unitrack.web.ui;

import java.util.List;
import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.web.github.GitHubRepoService;
import org.alexmond.unitrack.web.github.ProjectImportService;
import org.alexmond.unitrack.web.gitlab.GitLabRepoService;
import org.alexmond.unitrack.web.importing.ImportableRepo;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * "Import projects": lists the repositories a configured provider token can see and
 * provisions the selected ones as UniTrack projects. A {@code provider} tab switches
 * between GitHub ({@code unitrack.github.*}) and GitLab ({@code unitrack.gitlab.*}); both
 * reuse the same token as their commit-status integration. OAuth sign-in can supply
 * per-user tokens later.
 */
@Controller
@RequiredArgsConstructor
public class ImportController {

	private static final String GITHUB = "github";

	private static final String GITLAB = "gitlab";

	private final GitHubRepoService githubRepos;

	private final GitLabRepoService gitlabRepos;

	private final ProjectImportService importer;

	@GetMapping("/import")
	public String list(@RequestParam(name = "provider", defaultValue = GITHUB) String provider,
			@RequestParam(name = "q", required = false) String query, Model model) {
		String prov = normalize(provider);
		boolean configured = configured(prov);
		model.addAttribute("provider", prov);
		model.addAttribute("providerLabel", label(prov));
		model.addAttribute("configHint", configHint(prov));
		model.addAttribute("tabs", tabs(prov));
		model.addAttribute("configured", configured);
		model.addAttribute("query", query);
		List<? extends ImportableRepo> available = List.of();
		if (configured) {
			try {
				RepoPage page = fetch(prov);
				available = filter(page.repos(), query);
				model.addAttribute("truncated", page.truncated());
			}
			catch (RuntimeException ex) {
				model.addAttribute("error", "Could not list " + label(prov) + " repositories: " + ex.getMessage());
			}
		}
		model.addAttribute("repos", available);
		model.addAttribute("existing", importer.existingProjectNames());
		return "import";
	}

	@PostMapping("/import")
	public String submit(@RequestParam(name = "provider", defaultValue = GITHUB) String provider,
			@RequestParam(name = "repo", required = false) List<String> selected, RedirectAttributes ra) {
		String prov = normalize(provider);
		if (!configured(prov)) {
			return "redirect:/import?provider=" + prov;
		}
		if (selected == null || selected.isEmpty()) {
			ra.addFlashAttribute("error", "Select at least one repository to import.");
			return "redirect:/import?provider=" + prov;
		}
		ProjectImportService.Result result = this.importer.importRepos(fetch(prov).repos(), selected);
		StringBuilder msg = new StringBuilder("Imported ").append(result.imported().size()).append(" project(s)");
		if (!result.skipped().isEmpty()) {
			msg.append(" — skipped ").append(result.skipped().size()).append(" already present");
		}
		ra.addFlashAttribute("msg", msg.append('.').toString());
		return "redirect:/";
	}

	private RepoPage fetch(String prov) {
		if (GITLAB.equals(prov)) {
			GitLabRepoService.RepoList page = this.gitlabRepos.listRepos();
			return new RepoPage(page.repos(), page.truncated());
		}
		GitHubRepoService.RepoList page = this.githubRepos.listRepos();
		return new RepoPage(page.repos(), page.truncated());
	}

	private boolean configured(String prov) {
		return GITLAB.equals(prov) ? this.gitlabRepos.isConfigured() : this.githubRepos.isConfigured();
	}

	private List<Tab> tabs(String active) {
		return List.of(new Tab(GITHUB, "GitHub", this.githubRepos.isConfigured(), GITHUB.equals(active)),
				new Tab(GITLAB, "GitLab", this.gitlabRepos.isConfigured(), GITLAB.equals(active)));
	}

	private static String normalize(String provider) {
		return GITLAB.equalsIgnoreCase(provider) ? GITLAB : GITHUB;
	}

	private static String label(String prov) {
		return GITLAB.equals(prov) ? "GitLab" : "GitHub";
	}

	private static String configHint(String prov) {
		return GITLAB.equals(prov) ? "unitrack.gitlab.enabled=true and unitrack.gitlab.token (a token with api scope)"
				: "unitrack.github.enabled=true and unitrack.github.token (a token with repo scope)";
	}

	/** Case-insensitive substring filter on the repo full name (the search box). */
	private static List<? extends ImportableRepo> filter(List<? extends ImportableRepo> repos, String query) {
		if (query == null || query.isBlank()) {
			return repos;
		}
		String needle = query.trim().toLowerCase(Locale.ROOT);
		return repos.stream().filter((r) -> r.fullName().toLowerCase(Locale.ROOT).contains(needle)).toList();
	}

	/** A provider-neutral page of repositories, adapted from either provider's list. */
	private record RepoPage(List<? extends ImportableRepo> repos, boolean truncated) {
	}

	/** One provider tab in the import UI. */
	public record Tab(String id, String label, boolean configured, boolean active) {
	}

}
