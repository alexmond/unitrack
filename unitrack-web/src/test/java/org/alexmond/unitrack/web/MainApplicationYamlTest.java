package org.alexmond.unitrack.web;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the MAIN {@code application.yml}. Every {@code @SpringBootTest} loads
 * {@code src/test/resources/application.yml}, which shadows the real one — so a broken
 * main config (e.g. a duplicate {@code spring.ai} mapping key) sails through the whole
 * suite and only blows up at real startup. This parses the actual main file with Spring's
 * loader (strict: duplicate keys throw), so that class of mistake fails the build instead
 * of the deployment.
 */
class MainApplicationYamlTest {

	@Test
	void mainApplicationYamlParsesWithSpringsStrictLoader() {
		FileSystemResource main = new FileSystemResource("src/main/resources/application.yml");
		assertThat(main.exists()).as("main application.yml present at module-relative path").isTrue();

		List<PropertySource<?>> sources = catching(main);
		assertThat(sources).as("application.yml yielded at least one property document").isNotEmpty();
	}

	private static List<PropertySource<?>> catching(FileSystemResource main) {
		try {
			return new YamlPropertySourceLoader().load("main-application", main);
		}
		catch (Exception ex) {
			throw new AssertionError("main application.yml failed to parse: " + ex.getMessage(), ex);
		}
	}

}
