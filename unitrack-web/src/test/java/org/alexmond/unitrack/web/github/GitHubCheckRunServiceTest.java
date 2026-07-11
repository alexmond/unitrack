package org.alexmond.unitrack.web.github;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import org.alexmond.unitrack.domain.CoverageFileEntry;
import org.alexmond.unitrack.domain.CoverageReport;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.repository.CoverageFileEntryRepository;
import org.alexmond.unitrack.repository.CoverageReportRepository;
import org.alexmond.unitrack.repository.TestCaseResultRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubCheckRunServiceTest {

	@Test
	void addedLinesParsesNewFileLineNumbersFromAPatch() {
		// Hunk starts the new file at line 10; two adds, one context, one delete.
		String patch = "@@ -1,3 +10,4 @@\n context\n+added-11\n-removed\n+added-12";
		Set<Integer> added = GitHubCheckRunService.addedLines(patch);
		assertThat(added).containsExactlyInAnyOrder(11, 12);
	}

	@Test
	void addedLinesIsEmptyForANullPatch() {
		assertThat(GitHubCheckRunService.addedLines(null)).isEmpty();
	}

	@Test
	void returnsFalseWhenNoAppConfiguredSoStatusFallbackRuns() {
		GitHubAppTokenService appTokens = mock(GitHubAppTokenService.class);
		given(appTokens.isConfigured()).willReturn(false);
		GitHubAuth auth = mock(GitHubAuth.class);
		GitHubCheckRunService service = new GitHubCheckRunService(props(), RestClient.builder(),
				mock(GitHubConfigResolver.class), auth, appTokens, mock(CoverageReportRepository.class),
				mock(CoverageFileEntryRepository.class), mock(TestCaseResultRepository.class));

		assertThat(service.publish(mock(TestRun.class), null, null, 0)).isFalse();
		verifyNoInteractions(auth);
	}

	@Test
	void postsACheckRunWithAnnotationsOnUncoveredChangedLines() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		GitHubConfigResolver config = mock(GitHubConfigResolver.class);
		given(config.effective(anyLong()))
			.willReturn(new GitHubConfigResolver.Effective(true, "unitrack/quality-gate", true));
		GitHubAppTokenService appTokens = mock(GitHubAppTokenService.class);
		given(appTokens.isConfigured()).willReturn(true);
		GitHubAuth auth = mock(GitHubAuth.class);
		given(auth.bearerToken("o", "r")).willReturn("tok");

		CoverageReportRepository coverageReports = mock(CoverageReportRepository.class);
		CoverageReport report = mock(CoverageReport.class);
		given(report.getId()).willReturn(9L);
		given(coverageReports.findByRunId(100L)).willReturn(Optional.of(report));
		CoverageFileEntryRepository coverageFiles = mock(CoverageFileEntryRepository.class);
		CoverageFileEntry entry = new CoverageFileEntry(null, "org/ex", "Foo.java", 5, 2, 0, 0);
		entry.setUncoveredLines("10,12");
		given(coverageFiles.findByReportIdOrderByLineMissedDescPackageNameAsc(9L)).willReturn(List.of(entry));
		TestCaseResultRepository cases = mock(TestCaseResultRepository.class);
		given(cases.findByRunIdAndStatusInOrderByClassNameAscNameAsc(eq(100L), any())).willReturn(List.of());

		Project project = mock(Project.class);
		given(project.getId()).willReturn(1L);
		given(project.getRepoUrl()).willReturn("https://github.com/o/r");
		TestRun run = mock(TestRun.class);
		given(run.getProject()).willReturn(project);
		given(run.getCommitSha()).willReturn("sha1");
		given(run.getId()).willReturn(100L);
		given(run.getLineCoveragePct()).willReturn(80.0);

		server.expect(requestTo("https://api.github.com/repos/o/r/commits/sha1/pulls"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("[{\"number\":7}]", org.springframework.http.MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://api.github.com/repos/o/r/pulls/7/files?per_page=100"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(
					"[{\"filename\":\"mod/src/main/java/org/ex/Foo.java\",\"patch\":\"@@ -1,2 +10,3 @@\\n+a\\n+b\\n+c\"}]",
					org.springframework.http.MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://api.github.com/repos/o/r/check-runs"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.name").value("unitrack/quality-gate"))
			.andExpect(jsonPath("$.head_sha").value("sha1"))
			.andExpect(jsonPath("$.conclusion").value("success"))
			.andExpect(jsonPath("$.output.annotations.length()").value(2))
			.andExpect(jsonPath("$.output.annotations[0].path").value("mod/src/main/java/org/ex/Foo.java"))
			.andExpect(jsonPath("$.output.annotations[0].annotation_level").value("warning"))
			.andExpect(jsonPath("$.output.annotations[0].start_line").value(10))
			.andRespond(withStatus(HttpStatus.CREATED));

		GitHubCheckRunService service = new GitHubCheckRunService(props(), builder, config, auth, appTokens,
				coverageReports, coverageFiles, cases);

		assertThat(service.publish(run, null, 1.5, 0)).isTrue();
		server.verify();
	}

	private static GitHubProperties props() {
		GitHubProperties p = new GitHubProperties();
		p.setApiUrl("https://api.github.com");
		p.setServerBaseUrl("https://unitrack.example");
		return p;
	}

}
