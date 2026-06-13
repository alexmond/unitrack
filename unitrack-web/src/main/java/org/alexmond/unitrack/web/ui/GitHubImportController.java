package org.alexmond.unitrack.web.ui;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.web.github.GitHubRepo;
import org.alexmond.unitrack.web.github.GitHubRepoService;
import org.alexmond.unitrack.web.github.ProjectImportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * "Import from GitHub": lists the repositories the configured token can see and
 * provisions the selected ones as UniTrack projects. Uses the existing
 * {@code unitrack.github.*} token — OAuth sign-in can supply that token per-user later.
 */
@Controller
@RequiredArgsConstructor
public class GitHubImportController {

	private final GitHubRepoService repos;

	private final ProjectImportService importer;

	@GetMapping("/import")
	public String list(Model model) {
		boolean configured = repos.isConfigured();
		model.addAttribute("configured", configured);
		List<GitHubRepo> available = List.of();
		if (configured) {
			try {
				available = repos.listRepos();
			}
			catch (RuntimeException ex) {
				model.addAttribute("error", "Could not list GitHub repositories: " + ex.getMessage());
			}
		}
		model.addAttribute("repos", available);
		model.addAttribute("existing", importer.existingProjectNames());
		return "import";
	}

	@PostMapping("/import")
	public String submit(@RequestParam(name = "repo", required = false) List<String> selected, RedirectAttributes ra) {
		if (!repos.isConfigured()) {
			return "redirect:/import";
		}
		if (selected == null || selected.isEmpty()) {
			ra.addFlashAttribute("error", "Select at least one repository to import.");
			return "redirect:/import";
		}
		ProjectImportService.Result result = importer.importRepos(repos.listRepos(), selected);
		StringBuilder msg = new StringBuilder("Imported ").append(result.imported().size()).append(" project(s)");
		if (!result.skipped().isEmpty()) {
			msg.append(" — skipped ").append(result.skipped().size()).append(" already present");
		}
		ra.addFlashAttribute("msg", msg.append('.').toString());
		return "redirect:/";
	}

}
