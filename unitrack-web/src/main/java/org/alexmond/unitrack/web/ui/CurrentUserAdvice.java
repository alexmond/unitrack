package org.alexmond.unitrack.web.ui;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.web.security.SecurityProperties;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the signed-in username and available auth options to every server-rendered
 * page.
 */
@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class CurrentUserAdvice {

	private final SecurityProperties security;

	@ModelAttribute("currentUser")
	public String currentUser(Authentication auth) {
		boolean signedIn = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
		return signedIn ? auth.getName() : null;
	}

	/** Whether the signed-in user is a global admin (drives admin-only links). */
	@ModelAttribute("isAdmin")
	public boolean isAdmin(Authentication auth) {
		return auth != null && auth.isAuthenticated()
				&& auth.getAuthorities().stream().anyMatch((a) -> "ROLE_ADMIN".equals(a.getAuthority()));
	}

	/** Whether self-service signup is offered (drives the "Sign up" links). */
	@ModelAttribute("signupEnabled")
	public boolean signupEnabled() {
		return security.isSignupEnabled();
	}

}
