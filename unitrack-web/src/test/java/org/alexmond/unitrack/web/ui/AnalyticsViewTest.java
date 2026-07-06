package org.alexmond.unitrack.web.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AnalyticsView} — focused on the security-sensitive repo-link
 * builder.
 */
class AnalyticsViewTest {

	@Test
	void repoBaseAcceptsHttpUrlsAndStripsGitAndTrailingSlash() {
		assertThat(AnalyticsView.repoBase("https://github.com/o/r.git")).isEqualTo("https://github.com/o/r");
		assertThat(AnalyticsView.repoBase("https://github.com/o/r/")).isEqualTo("https://github.com/o/r");
		assertThat(AnalyticsView.repoBase("http://example.com/o/r")).isEqualTo("http://example.com/o/r");
	}

	@Test
	void repoBaseRejectsNonHttpSchemesToPreventXss() {
		// repoUrl is user-controlled and the base is emitted into a raw href — a script
		// scheme
		// must never become a clickable link.
		assertThat(AnalyticsView.repoBase("javascript:alert(1)")).isNull();
		assertThat(AnalyticsView.repoBase("JavaScript:alert(1)")).isNull();
		assertThat(AnalyticsView.repoBase("data:text/html,<script>alert(1)</script>")).isNull();
		assertThat(AnalyticsView.repoBase("ftp://example.com/o/r")).isNull();
		assertThat(AnalyticsView.repoBase("  ")).isNull();
		assertThat(AnalyticsView.repoBase(null)).isNull();
	}

}
