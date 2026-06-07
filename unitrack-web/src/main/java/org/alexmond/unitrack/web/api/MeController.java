package org.alexmond.unitrack.web.api;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.web.account.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authenticated principal (session or API token) — also serves as a token-auth check.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MeController {

	private final UserService users;

	@GetMapping("/me")
	public ResponseEntity<MeJson> me(Authentication auth) {
		if (auth == null || auth.getName() == null) {
			return ResponseEntity.status(401).build();
		}
		return users.findByUsername(auth.getName())
			.map((u) -> ResponseEntity
				.ok(new MeJson(u.getUsername(), u.getDisplayName(), u.getEmail(), u.getRole().name())))
			.orElseGet(() -> ResponseEntity.status(401).build());
	}

	public record MeJson(String username, String displayName, String email, String role) {
	}

}
