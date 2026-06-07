package org.alexmond.unitrack.web.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.unitrack.domain.Role;
import org.alexmond.unitrack.web.account.UserService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/** Seeds a default admin on first start when there are no users yet. */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultAdminSeeder implements ApplicationRunner {

	private final UserService users;

	private final SecurityProperties props;

	@Override
	public void run(ApplicationArguments args) {
		if (users.count() > 0) {
			return;
		}
		boolean generated = (props.getAdminPassword() == null || props.getAdminPassword().isBlank());
		String password = generated ? randomPassword() : props.getAdminPassword();
		users.create(props.getAdminUsername(), "Administrator", null, password, Role.ADMIN);
		if (generated) {
			log.warn("Created default admin '{}' with generated password: {}  (set unitrack.security.admin-password "
					+ "to control it)", props.getAdminUsername(), password);
		}
		else {
			log.info("Created default admin '{}'", props.getAdminUsername());
		}
	}

	private static String randomPassword() {
		byte[] bytes = new byte[12];
		new SecureRandom().nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
