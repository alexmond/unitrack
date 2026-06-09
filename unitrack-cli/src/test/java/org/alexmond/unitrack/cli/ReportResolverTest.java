package org.alexmond.unitrack.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ReportResolverTest {

	private final ReportResolver resolver = new ReportResolver();

	@Test
	void resolvesGlobToExistingFiles(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve("TEST-a.xml"), "<testsuite/>");
		Files.writeString(dir.resolve("TEST-b.xml"), "<testsuite/>");
		Files.writeString(dir.resolve("notes.txt"), "ignore me");

		List<?> matched = this.resolver.resolve(List.of(dir + "/TEST-*.xml"));

		assertThat(matched).hasSize(2);
	}

	@Test
	void noMatchYieldsEmptyList(@TempDir Path dir) {
		assertThat(this.resolver.resolve(List.of(dir + "/missing-*.xml"))).isEmpty();
	}

	@Test
	void ignoresNullAndBlankPatterns() {
		assertThat(this.resolver.resolve(null)).isEmpty();
		assertThat(this.resolver.resolve(List.of("   "))).isEmpty();
	}

}
