package org.alexmond.unitrack.web.ui;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.AlertChannel;
import org.alexmond.unitrack.domain.AlertChannelType;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.web.account.MembershipService;
import org.alexmond.unitrack.web.alert.AlertChannelService;
import org.alexmond.unitrack.web.account.ProjectAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-rendered management of a project's alert channels. Owner-only (canManage) for
 * mutations, since channels carry secrets and route externally. Secrets are shown masked
 * — the controller never renders a stored plaintext.
 */
@Controller
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AlertsUiController {

	private final AlertChannelService channels;

	private final ProjectAccessService access;

	private final MembershipService membership;

	@GetMapping("/projects/{id}/alerts")
	public String alerts(@PathVariable Long id, Model model) {
		Project project = access.requireReadProject(id);
		List<AlertChannel> list = channels.list(id);
		Map<Long, String> maskedSecrets = new LinkedHashMap<>();
		for (AlertChannel c : list) {
			maskedSecrets.put(c.getId(), AlertChannelService.mask(c.getSecret()));
		}
		model.addAttribute("project", project);
		model.addAttribute("channels", list);
		model.addAttribute("maskedSecrets", maskedSecrets);
		model.addAttribute("types", AlertChannelType.values());
		return "alerts";
	}

	@PostMapping("/projects/{id}/alerts")
	public String add(@PathVariable Long id, @RequestParam AlertChannelType type, @RequestParam String label,
			@RequestParam(required = false) String target, @RequestParam(required = false) String secret,
			@RequestParam(required = false) String tags, Authentication auth) {
		requireManage(id, auth);
		channels.add(id, type, label, target, secret, tags);
		return "redirect:/projects/" + id + "/alerts";
	}

	@PostMapping("/projects/{id}/alerts/{channelId}/delete")
	public String delete(@PathVariable Long id, @PathVariable Long channelId, Authentication auth) {
		requireManage(id, auth);
		channels.find(channelId)
			.filter((c) -> c.getProject().getId().equals(id))
			.ifPresent((c) -> channels.delete(channelId));
		return "redirect:/projects/" + id + "/alerts";
	}

	@PostMapping("/projects/{id}/alerts/{channelId}/toggle")
	public String toggle(@PathVariable Long id, @PathVariable Long channelId,
			@RequestParam(name = "enabled", defaultValue = "false") boolean enabled, Authentication auth) {
		requireManage(id, auth);
		channels.find(channelId)
			.filter((c) -> c.getProject().getId().equals(id))
			.ifPresent((c) -> channels.setEnabled(channelId, enabled));
		return "redirect:/projects/" + id + "/alerts";
	}

	private void requireManage(Long projectId, Authentication auth) {
		if (auth == null || !membership.canManage(auth.getName(), projectId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project owner access required");
		}
	}

}
