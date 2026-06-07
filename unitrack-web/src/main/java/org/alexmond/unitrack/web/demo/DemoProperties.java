package org.alexmond.unitrack.web.demo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Demo-data seeding, bound from {@code unitrack.demo.*}. Off by default. */
@Component
@ConfigurationProperties(prefix = "unitrack.demo")
@Getter
@Setter
public class DemoProperties {

	/** When true, seed a test user and sample projects/runs on startup (idempotent). */
	private boolean enabled;

	private String testUsername = "test";

	private String testPassword = "test";

}
