package org.alexmond.unitrack.web.ui;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Exposes the signed-in username to every server-rendered page (for the topbar). */
@ControllerAdvice(annotations = Controller.class)
public class CurrentUserAdvice {

	@ModelAttribute("currentUser")
	public String currentUser(Authentication auth) {
		boolean signedIn = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
		return signedIn ? auth.getName() : null;
	}

}
