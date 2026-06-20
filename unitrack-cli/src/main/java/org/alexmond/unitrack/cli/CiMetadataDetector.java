package org.alexmond.unitrack.cli;

import java.io.File;
import java.util.function.Function;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Infers build metadata (project, branch, commit, build URL, build number, repo, run key,
 * PR number) from the CI environment, so the uploader needs almost no flags. Supports
 * GitHub Actions, GitLab CI, Jenkins, CircleCI, Buildkite and Azure Pipelines; returns
 * {@link CiMetadata#empty()} when no known CI is detected.
 */
@Component
class CiMetadataDetector {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private final Function<String, String> env;

	CiMetadataDetector() {
		this(System::getenv);
	}

	CiMetadataDetector(Function<String, String> env) {
		this.env = env;
	}

	CiMetadata detect() {
		if (truthy("GITHUB_ACTIONS")) {
			return github();
		}
		if (truthy("GITLAB_CI")) {
			return gitlab();
		}
		if (present("JENKINS_URL")) {
			return jenkins();
		}
		if (truthy("CIRCLECI")) {
			return circleci();
		}
		if (truthy("BUILDKITE")) {
			return buildkite();
		}
		if (truthy("TF_BUILD")) {
			return azure();
		}
		return CiMetadata.empty();
	}

	private CiMetadata github() {
		String repo = env("GITHUB_REPOSITORY");
		String repoUrl = join(env("GITHUB_SERVER_URL"), repo);
		String runId = env("GITHUB_RUN_ID");
		String buildUrl = (repoUrl != null && runId != null) ? repoUrl + "/actions/runs/" + runId : null;
		String runKey = (runId != null) ? "gha-" + runId + suffix(env("GITHUB_RUN_ATTEMPT")) : null;
		String event = env("GITHUB_EVENT_NAME");
		String branch;
		String commit;
		String prNumber = null;
		if ("pull_request".equals(event) || "pull_request_target".equals(event)) {
			PrInfo pr = readPullRequest(env("GITHUB_EVENT_PATH"));
			branch = coalesce(pr.ref(), env("GITHUB_HEAD_REF"));
			commit = coalesce(pr.sha(), env("GITHUB_SHA"));
			prNumber = pr.number();
		}
		else {
			branch = env("GITHUB_REF_NAME");
			commit = env("GITHUB_SHA");
		}
		return new CiMetadata("github-actions", lastSegment(repo), branch, commit, buildUrl, env("GITHUB_RUN_NUMBER"),
				repoUrl, runKey, prNumber);
	}

	private CiMetadata gitlab() {
		String runKey = (env("CI_PIPELINE_ID") != null) ? "gitlab-" + env("CI_PIPELINE_ID") : null;
		String branch = coalesce(env("CI_MERGE_REQUEST_SOURCE_BRANCH_NAME"), env("CI_COMMIT_REF_NAME"));
		return new CiMetadata("gitlab-ci", env("CI_PROJECT_NAME"), branch, env("CI_COMMIT_SHA"), env("CI_JOB_URL"),
				env("CI_PIPELINE_IID"), env("CI_PROJECT_URL"), runKey, env("CI_MERGE_REQUEST_IID"));
	}

	private CiMetadata jenkins() {
		String runKey = (env("BUILD_TAG") != null) ? "jenkins-" + env("BUILD_TAG") : null;
		return new CiMetadata("jenkins", lastSegment(env("JOB_NAME")), stripOrigin(env("GIT_BRANCH")),
				env("GIT_COMMIT"), env("BUILD_URL"), env("BUILD_NUMBER"), env("GIT_URL"), runKey, null);
	}

	private CiMetadata circleci() {
		String runKey = "circle-" + coalesce(env("CIRCLE_WORKFLOW_ID"), env("CIRCLE_BUILD_NUM"));
		return new CiMetadata("circleci", env("CIRCLE_PROJECT_REPONAME"), env("CIRCLE_BRANCH"), env("CIRCLE_SHA1"),
				env("CIRCLE_BUILD_URL"), env("CIRCLE_BUILD_NUM"), env("CIRCLE_REPOSITORY_URL"), runKey,
				env("CIRCLE_PR_NUMBER"));
	}

	private CiMetadata buildkite() {
		String pr = env("BUILDKITE_PULL_REQUEST");
		String runKey = (env("BUILDKITE_BUILD_ID") != null) ? "buildkite-" + env("BUILDKITE_BUILD_ID") : null;
		return new CiMetadata("buildkite", env("BUILDKITE_PIPELINE_SLUG"), env("BUILDKITE_BRANCH"),
				env("BUILDKITE_COMMIT"), env("BUILDKITE_BUILD_URL"), env("BUILDKITE_BUILD_NUMBER"),
				env("BUILDKITE_REPO"), runKey, ("false".equals(pr)) ? null : pr);
	}

	private CiMetadata azure() {
		String runKey = (env("BUILD_BUILDID") != null) ? "azure-" + env("BUILD_BUILDID") : null;
		return new CiMetadata("azure-pipelines", env("BUILD_REPOSITORY_NAME"), env("BUILD_SOURCEBRANCHNAME"),
				env("BUILD_SOURCEVERSION"), null, env("BUILD_BUILDNUMBER"), env("BUILD_REPOSITORY_URI"), runKey, null);
	}

	private PrInfo readPullRequest(String eventPath) {
		if (eventPath == null || eventPath.isBlank()) {
			return PrInfo.EMPTY;
		}
		try {
			JsonNode root = MAPPER.readTree(new File(eventPath));
			JsonNode head = root.path("pull_request").path("head");
			JsonNode number = root.path("number");
			String num = (number.isMissingNode() || number.isNull()) ? null : String.valueOf(number.asInt());
			return new PrInfo(textOrNull(head.path("sha")), textOrNull(head.path("ref")), num);
		}
		catch (RuntimeException ex) {
			return PrInfo.EMPTY;
		}
	}

	private boolean truthy(String name) {
		return "true".equalsIgnoreCase(env(name));
	}

	private boolean present(String name) {
		String v = env(name);
		return v != null && !v.isBlank();
	}

	private String env(String name) {
		String v = this.env.apply(name);
		return (v != null && v.isBlank()) ? null : v;
	}

	private static String join(String base, String path) {
		return (base != null && path != null) ? base + "/" + path : null;
	}

	private static String suffix(String attempt) {
		return (attempt != null) ? "." + attempt : "";
	}

	private static String lastSegment(String path) {
		if (path == null) {
			return null;
		}
		int slash = path.lastIndexOf('/');
		return (slash >= 0) ? path.substring(slash + 1) : path;
	}

	private static String stripOrigin(String branch) {
		return (branch != null && branch.startsWith("origin/")) ? branch.substring("origin/".length()) : branch;
	}

	private static String coalesce(String a, String b) {
		return (a != null && !a.isBlank()) ? a : b;
	}

	private static String textOrNull(JsonNode node) {
		return (node.isMissingNode() || node.isNull()) ? null : node.asString();
	}

	/** The PR head ref/sha and number pulled from the GitHub event payload. */
	private record PrInfo(String sha, String ref, String number) {

		static final PrInfo EMPTY = new PrInfo(null, null, null);

	}

}
