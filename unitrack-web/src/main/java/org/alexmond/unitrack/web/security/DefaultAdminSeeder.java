package org.alexmond.unitrack.web.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.unitrack.domain.Role;
import org.alexmond.unitrack.web.account.UserService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/** Seeds a default admin on first start when there are no users yet. */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DefaultAdminSeeder implements ApplicationRunner {

	private final UserService users;

	private final SecurityProperties props;

	@Override
	public void run(ApplicationArguments args) {
		String username = props.getAdminUsername();
		boolean configured = props.getAdminPassword() != null && !props.getAdminPassword().isBlank();
		if (users.findByUsername(username).isEmpty()) {
			String password = configured ? props.getAdminPassword() : randomPassword();
			users.create(username, "Administrator", null, password, Role.ADMIN);
			if (configured) {
				log.info("Created default admin '{}'", username);
			}
			else {
				log.warn("Created default admin '{}' with generated password: {}  (set "
						+ "unitrack.security.admin-password to control it)", username, password);
			}
		}
		else if (configured) {
			// Keep the admin password in sync with the configured value.
			users.resetPassword(username, props.getAdminPassword());
		}
	}

	private static String randomPassword() {
		byte[] bytes = new byte[12];
		new SecureRandom().nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
