package org.alexmond.unitrack.web.ui;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.User;
import org.alexmond.unitrack.web.account.ApiTokenService;
import org.alexmond.unitrack.web.account.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Login page, user profile, and personal API token management. */
@Controller
@RequiredArgsConstructor
public class AccountController {

	private final UserService users;

	private final ApiTokenService tokens;

	@GetMapping("/login")
	public String login() {
		return "login";
	}

	@GetMapping("/profile")
	public String profile(Authentication auth, Model model) {
		User user = require(auth);
		model.addAttribute("user", user);
		model.addAttribute("tokens", tokens.listForUser(user.getId()));
		return "profile";
	}

	@PostMapping("/profile")
	public String updateProfile(Authentication auth, @RequestParam(required = false) String displayName,
			@RequestParam(required = false) String email,
			@RequestParam(name = "notifyGateFailure", defaultValue = "false") boolean notifyGateFailure,
			@RequestParam(name = "notifyTokenExpiry", defaultValue = "false") boolean notifyTokenExpiry,
			RedirectAttributes ra) {
		users.updateProfile(auth.getName(), displayName, email, notifyGateFailure, notifyTokenExpiry);
		ra.addFlashAttribute("msg", "Profile updated.");
		return "redirect:/profile";
	}

	@PostMapping("/profile/password")
	public String changePassword(Authentication auth, @RequestParam String currentPassword,
			@RequestParam String newPassword, RedirectAttributes ra) {
		if (users.changePassword(auth.getName(), currentPassword, newPassword)) {
			ra.addFlashAttribute("msg", "Password changed.");
		}
		else {
			ra.addFlashAttribute("error", "Current password is incorrect.");
		}
		return "redirect:/profile";
	}

	@PostMapping("/profile/tokens")
	public String createToken(Authentication auth, @RequestParam String name,
			@RequestParam(required = false) Integer expiresDays, @RequestParam(required = false) String scope,
			RedirectAttributes ra) {
		User user = require(auth);
		Instant expiresAt = (expiresDays != null && expiresDays > 0) ? Instant.now().plus(expiresDays, ChronoUnit.DAYS)
				: null;
		org.alexmond.unitrack.domain.TokenScope tokenScope = parseScope(scope);
		ra.addFlashAttribute("newToken", tokens.create(user, name, expiresAt, tokenScope).rawToken());
		return "redirect:/profile";
	}

	@PostMapping("/profile/tokens/{id}/revoke")
	public String revokeToken(Authentication auth, @PathVariable Long id, RedirectAttributes ra) {
		tokens.revoke(id, require(auth).getId());
		ra.addFlashAttribute("msg", "Token revoked.");
		return "redirect:/profile";
	}

	private static org.alexmond.unitrack.domain.TokenScope parseScope(String scope) {
		if ("INGEST".equalsIgnoreCase(scope)) {
			return org.alexmond.unitrack.domain.TokenScope.INGEST;
		}
		if ("ACTION".equalsIgnoreCase(scope)) {
			return org.alexmond.unitrack.domain.TokenScope.ACTION;
		}
		return org.alexmond.unitrack.domain.TokenScope.FULL;
	}

	private User require(Authentication auth) {
		return users.findByUsername(auth.getName())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in"));
	}

}
