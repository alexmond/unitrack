package org.alexmond.unitrack.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The configurable protected-branch patterns are globs ({@code *} = any run of
 * characters), so a site can match its own naming (e.g. {@code release/*}) without code
 * changes.
 */
class BranchGlobTest {

	@Test
	void globMatching() {
		assertThat(BranchService.globMatches("main", "main")).isTrue();
		assertThat(BranchService.globMatches("main", "maintenance")).isFalse();
		assertThat(BranchService.globMatches("release/*", "release/2.x")).isTrue();
		assertThat(BranchService.globMatches("release/*", "feature/x")).isFalse();
		assertThat(BranchService.globMatches("hotfix/*", "hotfix/urgent-42")).isTrue();
		assertThat(BranchService.globMatches("*", "anything-at-all")).isTrue();
		// A dot in the branch name is literal, not a regex wildcard.
		assertThat(BranchService.globMatches("v1.0", "v1x0")).isFalse();
	}

}
