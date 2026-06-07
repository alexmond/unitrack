package org.alexmond.unitrack.web.account;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.Role;
import org.alexmond.unitrack.domain.User;
import org.alexmond.unitrack.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Local user accounts: creation, profile edits, password changes. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	public Optional<User> findByUsername(String username) {
		return users.findByUsername(username);
	}

	public long count() {
		return users.count();
	}

	@Transactional
	public User create(String username, String displayName, String email, String rawPassword, Role role) {
		User user = new User(username, displayName, email, passwordEncoder.encode(rawPassword), role);
		return users.save(user);
	}

	/** Sets a user's password directly (admin/seed use). */
	@Transactional
	public void resetPassword(String username, String rawPassword) {
		require(username).setPasswordHash(passwordEncoder.encode(rawPassword));
	}

	@Transactional
	public void updateProfile(String username, String displayName, String email) {
		User user = require(username);
		user.setDisplayName(displayName);
		user.setEmail(email);
	}

	/**
	 * Changes the password after verifying the current one; returns false if it does not
	 * match.
	 */
	@Transactional
	public boolean changePassword(String username, String currentRaw, String newRaw) {
		User user = require(username);
		if (!passwordEncoder.matches(currentRaw, user.getPasswordHash())) {
			return false;
		}
		user.setPasswordHash(passwordEncoder.encode(newRaw));
		return true;
	}

	private User require(String username) {
		return users.findByUsername(username)
			.orElseThrow(() -> new IllegalArgumentException("Unknown user " + username));
	}

}
