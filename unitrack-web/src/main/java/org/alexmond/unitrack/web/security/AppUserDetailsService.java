package org.alexmond.unitrack.web.security;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.User;
import org.alexmond.unitrack.web.account.UserService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Adapts UniTrack {@link User}s to Spring Security for form login. */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

	private final UserService users;

	@Override
	public UserDetails loadUserByUsername(String username) {
		User user = users.findByUsername(username)
			.orElseThrow(() -> new UsernameNotFoundException("Unknown user " + username));
		return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
			.password(user.getPasswordHash())
			.authorities("ROLE_" + user.getRole().name())
			.build();
	}

}
