package org.alexmond.unitrack.web.github;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.alexmond.unitrack.domain.CoverageFileEntry;
import org.alexmond.unitrack.domain.CoverageReport;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.TestStatus;
import org.alexmond.unitrack.report.QualityGateResult;
import org.alexmond.unitrack.repository.CoverageFileEntryRepository;
import org.alexmond.unitrack.repository.CoverageReportRepository;
import org.alexmond.unitrack.repository.TestCaseResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Publishes a GitHub <em>check run</em> for a run's quality gate — a richer report than a
 * commit status: a markdown summary plus inline annotations on a PR's changed lines
 * (newly-uncovered coverage lines and new test failures). Requires GitHub App auth (the
 * Checks API rejects PATs), so {@link GitHubStatusService} stays as the fallback for
 * PAT-only deployments. Best-effort: failures are logged, never propagated to ingest.
 */
@Service
public class GitHubCheckRunService {

	private static final Logger log = LoggerFactory.getLogger(GitHubCheckRunService.class);

	/** GitHub accepts at most 50 annotations per check-run request. */
	private static final int MAX_ANNOTATIONS = 50;

	/** New-file line number from a unified-diff hunk header (@@ -a,b +c,d @@). */
	private static final Pattern HUNK = Pattern.compile("\\+(\\d+)");

	private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAP = new ParameterizedTypeReference<>() {
	};

	private final GitHubProperties props;

	private final RestClient restClient;

	private final GitHubConfigResolver config;

	private final GitHubAuth auth;

	private final GitHubAppTokenService appTokens;

	private final CoverageReportRepository coverageReports;

	private final CoverageFileEntryRepository coverageFiles;

	private final TestCaseResultRepository cases;

	public GitHubCheckRunService(GitHubProperties props, RestClient.Builder restClientBuilder,
			GitHubConfigResolver config, GitHubAuth auth, GitHubAppTokenService appTokens,
			CoverageReportRepository coverageReports, CoverageFileEntryRepository coverageFiles,
			TestCaseResultRepository cases) {
		this.props = props;
		this.restClient = restClientBuilder.build();
		this.config = config;
		this.auth = auth;
		this.appTokens = appTokens;
		this.coverageReports = coverageReports;
		this.coverageFiles = coverageFiles;
		this.cases = cases;
	}

	/**
	 * Posts a check run for the run. Returns {@code true} when the App path owns GitHub
	 * reporting (so the caller skips the classic commit status), {@code false} when no
	 * App is configured and the status fallback should run instead.
	 */
	public boolean publish(TestRun run, QualityGateResult gate, Double coverageDelta, int newFailures) {
		if (!this.appTokens.isConfigured()) {
			return false;
		}
		GitHubConfigResolver.Effective cfg = this.config.effective(run.getProject().getId());
		String[] repo = GitHubStatusService.parseOwnerRepo(run.getProject().getRepoUrl());
		String sha = run.getCommitSha();
		if (!cfg.enabled() || repo == null || sha == null || sha.isBlank()) {
			return true;
		}
		String bearer = this.auth.bearerToken(repo[0], repo[1]);
		if (bearer == null) {
			return true;
		}
		try {
			List<PrFile> prFiles = prFiles(repo, sha, bearer);
			List<Map<String, Object>> annotations = annotations(run, prFiles);
			boolean passed = (gate == null) || gate.passed();
			Map<String, Object> output = Map.of("title", passed ? "Quality gate passed" : "Quality gate failed",
					"summary", summary(run, gate, coverageDelta, newFailures, annotations.size()), "annotations",
					annotations);
			Map<String, Object> body = Map.of("name", cfg.context(), "head_sha", sha, "status", "completed",
					"conclusion", passed ? "success" : "failure", "details_url",
					this.props.getServerBaseUrl() + "/runs/" + run.getId(), "output", output);
			this.restClient.post()
				.uri(this.props.getApiUrl() + "/repos/{owner}/{repo}/check-runs", repo[0], repo[1])
				.headers((h) -> authHeaders(h, bearer))
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.toBodilessEntity();
			log.info("Posted GitHub check run for {}/{}@{}: {} ({} annotations)", repo[0], repo[1], sha,
					body.get("conclusion"), annotations.size());
		}
		catch (RuntimeException ex) {
			log.warn("Failed to post GitHub check run for {}/{}@{}: {}", repo[0], repo[1], sha, ex.getMessage());
		}
		return true;
	}

	/** The PR's changed files with their added-line numbers, or empty when not a PR. */
	private List<PrFile> prFiles(String[] repo, String sha, String bearer) {
		List<Map<String, Object>> pulls = this.restClient.get()
			.uri(this.props.getApiUrl() + "/repos/{owner}/{repo}/commits/{sha}/pulls", repo[0], repo[1], sha)
			.headers((h) -> authHeaders(h, bearer))
			.retrieve()
			.body(LIST_OF_MAP);
		if (pulls == null || pulls.isEmpty()) {
			return List.of();
		}
		Number pr = (Number) pulls.get(0).get("number");
		List<Map<String, Object>> files = this.restClient.get()
			.uri(this.props.getApiUrl() + "/repos/{owner}/{repo}/pulls/{pr}/files?per_page=100", repo[0], repo[1],
					pr.intValue())
			.headers((h) -> authHeaders(h, bearer))
			.retrieve()
			.body(LIST_OF_MAP);
		List<PrFile> result = new ArrayList<>();
		if (files != null) {
			for (Map<String, Object> f : files) {
				result.add(new PrFile((String) f.get("filename"), addedLines((String) f.get("patch"))));
			}
		}
		return result;
	}

	/** Failure annotations first (higher value), then newly-uncovered coverage lines. */
	private List<Map<String, Object>> annotations(TestRun run, List<PrFile> prFiles) {
		List<Map<String, Object>> out = new ArrayList<>();
		if (prFiles.isEmpty()) {
			return out;
		}
		failureAnnotations(run, prFiles, out);
		coverageAnnotations(run, prFiles, out);
		return (out.size() <= MAX_ANNOTATIONS) ? out : out.subList(0, MAX_ANNOTATIONS);
	}

	/**
	 * A "not covered by tests" annotation for each uncovered line that the PR changed.
	 */
	private void coverageAnnotations(TestRun run, List<PrFile> prFiles, List<Map<String, Object>> out) {
		CoverageReport report = this.coverageReports.findByRunId(run.getId()).orElse(null);
		if (report == null) {
			return;
		}
		for (CoverageFileEntry entry : this.coverageFiles
			.findByReportIdOrderByLineMissedDescPackageNameAsc(report.getId())) {
			if (entry.getUncoveredLines() == null || out.size() >= MAX_ANNOTATIONS) {
				continue;
			}
			PrFile match = matchBySuffix(prFiles, entry.getPath());
			if (match == null) {
				continue;
			}
			for (String token : entry.getUncoveredLines().split(",")) {
				int line = parseInt(token);
				if (line > 0 && match.addedLines().contains(line) && out.size() < MAX_ANNOTATIONS) {
					out.add(annotation(match.filename(), line, "warning", "This line is not covered by tests."));
				}
			}
		}
	}

	/**
	 * A failure annotation on the test's own source line for each new failure in a
	 * changed file.
	 */
	private void failureAnnotations(TestRun run, List<PrFile> prFiles, List<Map<String, Object>> out) {
		List<TestCaseResult> failed = this.cases.findByRunIdAndStatusInOrderByClassNameAscNameAsc(run.getId(),
				List.of(TestStatus.FAILED, TestStatus.ERROR));
		for (TestCaseResult c : failed) {
			if (out.size() >= MAX_ANNOTATIONS || c.getClassName() == null || c.getClassName().isBlank()) {
				continue;
			}
			String simple = c.getClassName().substring(c.getClassName().lastIndexOf('.') + 1);
			PrFile match = matchBySuffix(prFiles, c.getClassName().replace('.', '/') + ".java");
			int line = stacktraceLine(c.getFailureStacktrace(), simple);
			if (match != null && line > 0) {
				out.add(annotation(match.filename(), line, "failure", failureMessage(c)));
			}
		}
	}

	private static Map<String, Object> annotation(String path, int line, String level, String message) {
		return Map.of("path", path, "start_line", line, "end_line", line, "annotation_level", level, "message",
				(message.length() <= 640) ? message : message.substring(0, 640));
	}

	private static String failureMessage(TestCaseResult c) {
		StringBuilder sb = new StringBuilder(c.getName());
		sb.append(" failed");
		if (c.getFailureMessage() != null && !c.getFailureMessage().isBlank()) {
			sb.append(": ").append(c.getFailureMessage().strip());
		}
		return sb.toString();
	}

	/**
	 * The source line of the first stack frame in the test's own class (its assertion
	 * site), or 0 when the trace has no such frame.
	 */
	private static int stacktraceLine(String stacktrace, String simpleClassName) {
		if (stacktrace == null) {
			return 0;
		}
		Matcher m = Pattern.compile(Pattern.quote(simpleClassName) + "\\.java:(\\d+)").matcher(stacktrace);
		return m.find() ? parseInt(m.group(1)) : 0;
	}

	/**
	 * The PR file whose repo-relative path ends with the coverage/class path — coverage
	 * paths are package-relative (no {@code src/main/java} prefix), so a suffix match
	 * bridges to the PR's repo-relative filename.
	 */
	private static PrFile matchBySuffix(List<PrFile> prFiles, String path) {
		for (PrFile f : prFiles) {
			if (f.filename().equals(path) || f.filename().endsWith("/" + path)) {
				return f;
			}
		}
		return null;
	}

	/** New-file line numbers added by a unified-diff patch. */
	static Set<Integer> addedLines(String patch) {
		Set<Integer> added = new HashSet<>();
		if (patch == null) {
			return added;
		}
		int newLine = 0;
		for (String line : patch.split("\n")) {
			if (line.startsWith("@@")) {
				Matcher m = HUNK.matcher(line);
				if (m.find()) {
					newLine = parseInt(m.group(1));
				}
			}
			else if (line.startsWith("+")) {
				added.add(newLine);
				newLine++;
			}
			else if (!line.startsWith("-")) {
				newLine++;
			}
		}
		return added;
	}

	private String summary(TestRun run, QualityGateResult gate, Double coverageDelta, int newFailures, int annCount) {
		StringBuilder sb = new StringBuilder();
		sb.append("**Gate:** ").append((gate != null) ? gate.status() : "n/a");
		sb.append(" · **Tests:** ")
			.append(run.getPassed())
			.append(" passed, ")
			.append(run.getFailed() + run.getErrors())
			.append(" failed");
		if (run.getLineCoveragePct() != null) {
			sb.append("\n\n**Coverage:** ").append(String.format(Locale.ROOT, "%.1f%%", run.getLineCoveragePct()));
			if (coverageDelta != null) {
				sb.append(String.format(Locale.ROOT, " (%+.1fpp vs base)", coverageDelta));
			}
		}
		if (newFailures > 0) {
			sb.append("\n\n**New failures:** ").append(newFailures);
		}
		if (annCount > 0) {
			sb.append("\n\n_").append(annCount).append(" annotation(s) on changed lines._");
		}
		sb.append("\n\n[View full run](").append(this.props.getServerBaseUrl()).append("/runs/").append(run.getId());
		sb.append(')');
		return sb.toString();
	}

	private void authHeaders(HttpHeaders headers, String bearer) {
		headers.set("Authorization", "Bearer " + bearer);
		headers.set("Accept", "application/vnd.github+json");
		headers.set("X-GitHub-Api-Version", "2022-11-28");
	}

	private static int parseInt(String s) {
		try {
			return Integer.parseInt(s.trim());
		}
		catch (NumberFormatException ex) {
			return 0;
		}
	}

	/** A PR's changed file with the set of new-file line numbers it added. */
	private record PrFile(String filename, Set<Integer> addedLines) {
	}

}
