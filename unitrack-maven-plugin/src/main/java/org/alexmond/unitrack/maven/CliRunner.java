package org.alexmond.unitrack.maven;

import java.util.List;

import org.alexmond.unitrack.cli.UnitrackCliApplication;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Runs the {@code unitrack-cli} engine in-process and returns its exit code — so the
 * Maven plugin reuses the exact upload/auto-detect/retry/gate logic with no duplication.
 * The CLI's {@code CommandLineRunner} executes during startup; we read the exit code
 * instead of letting {@code main} call {@code System.exit}.
 */
final class CliRunner {

	private CliRunner() {
	}

	static int run(List<String> args) {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(UnitrackCliApplication.class)
			.web(WebApplicationType.NONE)
			.bannerMode(Banner.Mode.OFF)
			.run(args.toArray(new String[0]))) {
			return SpringApplication.exit(context);
		}
	}

}
