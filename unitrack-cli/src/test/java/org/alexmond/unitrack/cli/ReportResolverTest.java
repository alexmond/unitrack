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
	void resolvesLeadingWildcardRelativeToWorkingDir() throws IOException {
		// The documented glob form "**/surefire-reports/*.xml" starts with a wildcard; it
		// must
		// resolve against the working directory (a leading "**" used to leave the
		// resolver with
		// a bare "file:" root and match nothing).
		Files.createDirectories(Path.of("target"));
		Path subdir = Files.createTempDirectory(Path.of("target"), "rrtest-");
		try {
			Files.writeString(subdir.resolve("TEST-leading.xml"), "<testsuite/>");
			assertThat(this.resolver.resolve(List.of("**/TEST-leading.xml"))).isNotEmpty();
		}
		finally {
			Files.walk(subdir).sorted(java.util.Comparator.reverseOrder()).forEach((p) -> {
				try {
					Files.delete(p);
				}
				catch (IOException ignored) {
				}
			});
		}
	}

	@Test
	void ignoresNullAndBlankPatterns() {
		assertThat(this.resolver.resolve(null)).isEmpty();
		assertThat(this.resolver.resolve(List.of("   "))).isEmpty();
	}

}
