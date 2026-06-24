package org.alexmond.unitrack.it;

import java.nio.file.Path;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Target + task settings, bound from {@code unitrack.it.*}. The active Spring profile
 * selects the environment: the default (local) targets a locally-running instance,
 * {@code lab}/{@code prod} target the deployed ones (see the per-profile
 * {@code application-*.yml}).
 */
@ConfigurationProperties(prefix = "unitrack.it")
@Getter
@Setter
public class ItProperties {

	/** Base URL of the UniTrack instance the tasks drive. */
	private String baseUrl = "http://localhost:8080";

	/** API token for {@code /api/**} calls against a closed-mode instance (optional). */
	private String token;

	/** Directory screenshots are written to. */
	private Path screenshotDir = Path.of("target", "screenshots");

}
