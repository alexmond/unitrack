package org.alexmond.unitrack.web.ui;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.web.account.UserService;
import org.alexmond.unitrack.web.security.SecurityProperties;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Self-service signup for local accounts. Gated by
 * {@code unitrack.security.signup-enabled}; when disabled both endpoints redirect to the
 * login page. Other auth methods (e.g. GitHub OAuth) will be added as separate,
 * independently-toggled flows.
 */
@Controller
@RequiredArgsConstructor
public class SignupController {

	private static final int MIN_PASSWORD_LENGTH = 8;

	private final SecurityProperties security;

	private final UserService users;

	private final UserDetailsService userDetailsService;

	private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();

	private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

	@GetMapping("/signup")
	public String form() {
		return security.isSignupEnabled() ? "signup" : "redirect:/login";
	}

	@PostMapping("/signup")
	public String submit(@RequestParam String username, @RequestParam(required = false) String email,
			@RequestParam String password, HttpServletRequest request, HttpServletResponse response, Model model) {
		if (!security.isSignupEnabled()) {
			return "redirect:/login";
		}
		String trimmed = (username != null) ? username.trim() : "";
		if (trimmed.isEmpty() || password == null || password.length() < MIN_PASSWORD_LENGTH) {
			return reject(model, trimmed, email,
					"Username is required and the password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
		}
		try {
			users.register(trimmed, email, password);
		}
		catch (IllegalArgumentException ex) {
			return reject(model, trimmed, email, ex.getMessage());
		}
		login(trimmed, request, response);
		return "redirect:/profile";
	}

	private String reject(Model model, String username, String email, String error) {
		model.addAttribute("error", error);
		model.addAttribute("username", username);
		model.addAttribute("email", email);
		return "signup";
	}

	/** Establish an authenticated session for the freshly-created user. */
	private void login(String username, HttpServletRequest request, HttpServletResponse response) {
		UserDetails details = userDetailsService.loadUserByUsername(username);
		UsernamePasswordAuthenticationToken auth = UsernamePasswordAuthenticationToken.authenticated(details, null,
				details.getAuthorities());
		SecurityContext context = holderStrategy.createEmptyContext();
		context.setAuthentication(auth);
		holderStrategy.setContext(context);
		contextRepository.saveContext(context, request, response);
	}

}
