package org.alexmond.unitrack.web.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the login-gated "New summary" page. Additive and self-contained: the route is
 * forced to require authentication in {@code SecurityConfig} (even in open mode, like
 * {@code /profile}), and its nav link renders only for signed-in users.
 */
@Controller
public class NewSummaryController {

	@GetMapping("/new-summary")
	String newSummary() {
		return "new-summary";
	}

}
