package org.alexmond.unitrack.web.ui;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.ShareLink;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.User;
import org.alexmond.unitrack.web.account.ProjectAccessService;
import org.alexmond.unitrack.web.account.ShareLinkService;
import org.alexmond.unitrack.web.account.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Public, capability-based share links to a run. {@code GET /share/{token}} renders a
 * read-only report for anyone holding the token (no nav, no auth, project visibility not
 * consulted — the token is the grant). Creating/revoking a link requires write access to
 * the run's project.
 */
@Controller
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ShareController {

	private final ShareLinkService shareLinks;

	private final RunReportAssembler assembler;

	private final ProjectAccessService access;

	private final UserService users;

	@GetMapping("/share/{token}")
	public String view(@PathVariable String token, Model model) {
		TestRun run = shareLinks.resolve(token)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Share link not found"));
		assembler.populate(model, run);
		model.addAttribute("shared", true);
		return "run-export";
	}

	@PostMapping("/runs/{id}/share")
	public String create(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
		TestRun run = access.requireReadRun(id);
		access.requireWrite(run.getProject());
		User creator = users.findByUsername(auth.getName())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in"));
		String raw = shareLinks.create(run, creator).rawToken();
		String url = ServletUriComponentsBuilder.fromCurrentContextPath()
			.path("/share/")
			.path(raw)
			.build()
			.toUriString();
		ra.addFlashAttribute("newShareUrl", url);
		return "redirect:/runs/" + id;
	}

	@PostMapping("/runs/{id}/share/{linkId}/revoke")
	public String revoke(@PathVariable Long id, @PathVariable Long linkId, RedirectAttributes ra) {
		ShareLink link = shareLinks.find(linkId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Share link not found"));
		// Authorize against the link's own run, and ensure the path id matches it.
		if (!link.getRun().getId().equals(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Share link not found");
		}
		access.requireWrite(link.getRun().getProject());
		shareLinks.revoke(linkId);
		ra.addFlashAttribute("msg", "Share link revoked.");
		return "redirect:/runs/" + id;
	}

}
