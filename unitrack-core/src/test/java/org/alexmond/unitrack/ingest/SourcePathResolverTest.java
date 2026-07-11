package org.alexmond.unitrack.ingest;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourcePathResolverTest {

	private static final List<String> MANIFEST = List.of(
			"unitrack-core/src/main/java/org/alexmond/unitrack/report/Foo.java",
			"unitrack-web/src/main/java/org/alexmond/unitrack/web/Bar.java", "README.md");

	@Test
	void resolvesPackageRelativePathToRepoRelativeBySuffix() {
		String resolved = SourcePathResolver.resolve("org/alexmond/unitrack/report/Foo.java", null, MANIFEST);
		assertThat(resolved).isEqualTo("unitrack-core/src/main/java/org/alexmond/unitrack/report/Foo.java");
	}

	@Test
	void prefersMatchUnderTheKnownModule() {
		List<String> manifest = List.of("mod-a/src/main/java/org/ex/Dup.java", "mod-b/src/main/java/org/ex/Dup.java");
		assertThat(SourcePathResolver.resolve("org/ex/Dup.java", "mod-b", manifest))
			.isEqualTo("mod-b/src/main/java/org/ex/Dup.java");
	}

	@Test
	void fallsBackToFirstSuffixMatchWhenModuleDoesNotMatch() {
		List<String> manifest = List.of("mod-a/src/main/java/org/ex/Dup.java", "mod-b/src/main/java/org/ex/Dup.java");
		assertThat(SourcePathResolver.resolve("org/ex/Dup.java", "mod-c", manifest))
			.isEqualTo("mod-a/src/main/java/org/ex/Dup.java");
	}

	@Test
	void returnsNullWhenNothingMatches() {
		assertThat(SourcePathResolver.resolve("org/ex/Missing.java", null, MANIFEST)).isNull();
	}

	@Test
	void returnsNullForEmptyOrAbsentManifest() {
		assertThat(SourcePathResolver.resolve("org/ex/Foo.java", null, List.of())).isNull();
		assertThat(SourcePathResolver.resolve("org/ex/Foo.java", null, null)).isNull();
		assertThat(SourcePathResolver.resolve(null, null, MANIFEST)).isNull();
	}

	@Test
	void matchesAnExactManifestEntryWithNoLeadingSegment() {
		assertThat(SourcePathResolver.resolve("Foo.java", null, List.of("Foo.java"))).isEqualTo("Foo.java");
	}

}
