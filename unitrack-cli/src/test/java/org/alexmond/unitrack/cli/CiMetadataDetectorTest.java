package org.alexmond.unitrack.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class CiMetadataDetectorTest {

	private static CiMetadata detect(Map<String, String> env) {
		return new CiMetadataDetector(env::get).detect();
	}

	@Test
	void noKnownCiYieldsEmpty() {
		assertThat(detect(Map.of()).ciProvider()).isNull();
	}

	@Test
	void githubPushBuildsMetadataFromEnv() {
		CiMetadata m = detect(Map.of("GITHUB_ACTIONS", "true", "GITHUB_REPOSITORY", "octo/myapp", "GITHUB_SERVER_URL",
				"https://github.com", "GITHUB_RUN_ID", "99", "GITHUB_RUN_ATTEMPT", "2", "GITHUB_EVENT_NAME", "push",
				"GITHUB_REF_NAME", "main", "GITHUB_SHA", "abc123"));

		assertThat(m.ciProvider()).isEqualTo("github-actions");
		assertThat(m.project()).isEqualTo("myapp");
		assertThat(m.branch()).isEqualTo("main");
		assertThat(m.commit()).isEqualTo("abc123");
		assertThat(m.repoUrl()).isEqualTo("https://github.com/octo/myapp");
		assertThat(m.buildUrl()).isEqualTo("https://github.com/octo/myapp/actions/runs/99");
		assertThat(m.runKey()).isEqualTo("gha-99.2");
	}

	@Test
	void githubPullRequestReadsHeadShaFromEventPayload(@TempDir Path dir) throws IOException {
		Path event = dir.resolve("event.json");
		Files.writeString(event,
				"{\"number\":7,\"pull_request\":{\"head\":{\"ref\":\"feature\",\"sha\":\"headsha\"}}}");
		CiMetadata m = detect(Map.of("GITHUB_ACTIONS", "true", "GITHUB_REPOSITORY", "octo/myapp", "GITHUB_RUN_ID", "5",
				"GITHUB_EVENT_NAME", "pull_request", "GITHUB_EVENT_PATH", event.toString(), "GITHUB_HEAD_REF",
				"feature", "GITHUB_SHA", "mergesha"));

		// The PR head SHA (not the synthetic merge SHA) must win.
		assertThat(m.commit()).isEqualTo("headsha");
		assertThat(m.branch()).isEqualTo("feature");
		assertThat(m.prNumber()).isEqualTo("7");
	}

	@Test
	void gitlabUsesCiVars() {
		CiMetadata m = detect(Map.of("GITLAB_CI", "true", "CI_PROJECT_NAME", "svc", "CI_COMMIT_SHA", "sha1",
				"CI_COMMIT_REF_NAME", "dev", "CI_PIPELINE_ID", "321", "CI_JOB_URL", "https://gl/job/1",
				"CI_PROJECT_URL", "https://gl/group/svc"));

		assertThat(m.ciProvider()).isEqualTo("gitlab-ci");
		assertThat(m.project()).isEqualTo("svc");
		assertThat(m.commit()).isEqualTo("sha1");
		assertThat(m.branch()).isEqualTo("dev");
		assertThat(m.buildUrl()).isEqualTo("https://gl/job/1");
		assertThat(m.runKey()).isEqualTo("gitlab-321");
	}

	@Test
	void jenkinsStripsOriginPrefixAndUsesJobName() {
		CiMetadata m = detect(Map.of("JENKINS_URL", "https://jenkins", "JOB_NAME", "team/myapp", "GIT_COMMIT", "j1",
				"GIT_BRANCH", "origin/main", "BUILD_URL", "https://jenkins/job/5", "BUILD_TAG", "jenkins-myapp-5"));

		assertThat(m.ciProvider()).isEqualTo("jenkins");
		assertThat(m.project()).isEqualTo("myapp");
		assertThat(m.branch()).isEqualTo("main");
		assertThat(m.commit()).isEqualTo("j1");
		assertThat(m.runKey()).isEqualTo("jenkins-jenkins-myapp-5");
	}

	@Test
	void circleciUsesCircleVars() {
		CiMetadata m = detect(Map.of("CIRCLECI", "true", "CIRCLE_PROJECT_REPONAME", "app", "CIRCLE_SHA1", "c1",
				"CIRCLE_BRANCH", "trunk", "CIRCLE_BUILD_URL", "https://circle/1", "CIRCLE_WORKFLOW_ID", "wf-1"));

		assertThat(m.ciProvider()).isEqualTo("circleci");
		assertThat(m.commit()).isEqualTo("c1");
		assertThat(m.branch()).isEqualTo("trunk");
		assertThat(m.runKey()).isEqualTo("circle-wf-1");
	}

}
