package org.alexmond.unitrack.web.api;

import org.alexmond.unitrack.domain.PerfRun;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.ProjectRole;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.ingest.IngestException;
import org.alexmond.unitrack.ingest.IngestRequest;
import org.alexmond.unitrack.ingest.IngestService;
import org.alexmond.unitrack.ingest.PerfIngestService;
import org.alexmond.unitrack.report.ReportingService;
import org.alexmond.unitrack.report.PerfRegressionResult;
import org.alexmond.unitrack.report.PerfRegressionService;
import org.alexmond.unitrack.report.PerfRunRegression;
import org.alexmond.unitrack.report.PerfRunRegressionService;
import org.alexmond.unitrack.report.QualityGateResult;
import org.alexmond.unitrack.report.QualityGateService;
import org.alexmond.unitrack.report.TestRegressionResult;
import org.alexmond.unitrack.report.TestRegressionService;
import org.alexmond.unitrack.web.account.AuditService;
import org.alexmond.unitrack.web.account.MembershipService;
import org.alexmond.unitrack.web.account.ProjectAccessService;
import org.alexmond.unitrack.web.github.GitHubPrCommentService;
import org.alexmond.unitrack.web.github.GitHubStatusService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.alexmond.unitrack.web.alert.AlertEventPublisher;
import org.alexmond.unitrack.web.live.LiveEventService;
import org.alexmond.unitrack.web.live.RunUpdate;
import org.alexmond.unitrack.web.notify.GateFailureNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Accepts CI uploads of test + coverage reports.
 *
 * <pre>
 * curl -F project=myapp -F branch=main -F commit=$SHA \
 *      -F 'junit=@target/surefire-reports/TEST-*.xml' \
 *      -F 'jacoco=@target/site/jacoco/jacoco.xml' \
 *      http://localhost:8080/api/v1/ingest
 * </pre>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class IngestController {

	private final IngestService ingestService;

	private final QualityGateService qualityGate;

	private final GitHubStatusService gitHubStatus;

	private final GitHubPrCommentService gitHubPrComment;

	private final TestRegressionService testRegression;

	private final PerfRegressionService perfRegression;

	private final PerfIngestService perfIngest;

	private final PerfRunRegressionService perfRunRegression;

	private final GateFailureNotifier gateFailureNotifier;

	private final org.alexmond.unitrack.web.notify.OwnerFailureNotifier ownerFailureNotifier;

	private final AlertEventPublisher alertEvents;

	private final LiveEventService liveEvents;

	private final ReportingService reporting;

	private final MembershipService membership;

	private final ProjectAccessService access;

	private final ObservationRegistry observationRegistry;

	private final AuditService audit;

	private final org.alexmond.unitrack.web.gitlab.GitLabService gitLab;

	@PostMapping(path = "/ingest", consumes = "multipart/form-data")
	public ResponseEntity<ApiResponses.IngestResultJson> ingest(@RequestParam String project,
			@RequestParam(required = false) String repoUrl, @RequestParam(required = false) String branch,
			@RequestParam(required = false) String flag, @RequestParam(required = false) String commit,
			@RequestParam(required = false) String buildUrl, @RequestParam(required = false) String buildName,
			@RequestParam(required = false) String ciProvider, @RequestParam(required = false) String runKey,
			@RequestParam(required = false) String baseBranch, @RequestParam(required = false) Integer prNumber,
			@RequestParam(name = "junit", required = false) List<MultipartFile> junit,
			@RequestParam(name = "jacoco", required = false) List<MultipartFile> jacoco,
			@RequestParam(name = "perf", required = false) List<MultipartFile> perf) {

		List<Supplier<InputStream>> junitStreams = toSuppliers(junit);
		List<Supplier<InputStream>> perfStreams = toSuppliers(perf);
		if (junitStreams.isEmpty() && perfStreams.isEmpty()) {
			throw new IngestException("Provide at least one 'junit' or 'perf' file");
		}

		// Authorize against the existing project; a brand-new project will be created by
		// ingest.
		String uploader = access.currentUsername();
		Project existing = reporting.findProjectByName(project).orElse(null);
		if (existing != null && uploader != null && !membership.canWrite(uploader, existing.getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Write access to project required");
		}

		IngestRequest meta = new IngestRequest(project, repoUrl, branch, flag, commit, buildUrl, buildName, ciProvider,
				runKey, baseBranch, prNumber);

		// One Observation around the unit of work: Boot derives the metric (Timer) and
		// the
		// span from it. Low-cardinality dimensions become metric tags; ids/names are
		// trace-only (high-cardinality) to avoid metric tag explosion.
		Observation observation = Observation.createNotStarted("unitrack.ingest", observationRegistry)
			.lowCardinalityKeyValue("kind",
					junitStreams.isEmpty() ? "perf" : (perfStreams.isEmpty() ? "tests" : "tests+perf"))
			.lowCardinalityKeyValue("has_coverage", String.valueOf((jacoco != null) && !jacoco.isEmpty()))
			.highCardinalityKeyValue("project", project);

		return observation.observe(() -> {
			TestRun run = null;
			if (!junitStreams.isEmpty()) {
				run = ingestService.ingest(meta, junitStreams, toSuppliers(jacoco));
				// Post-ingest publishing as an explicit child span — parent passed
				// through
				// (not via thread-local), so it nests correctly even if moved off-thread.
				TestRun ingested = run;
				Observation.createNotStarted("unitrack.report", observationRegistry)
					.parentObservation(observation)
					.observe(() -> publishGitHubStatus(ingested));
			}
			PerfRun perfRun = perfStreams.isEmpty() ? null : perfIngest.ingest(meta, perfStreams);
			if (perfRun != null) {
				publishPerfComment(perfRun);
			}
			// A newly-created (PRIVATE by default) project gets its uploader as OWNER, so
			// an
			// authenticated CI/user keeps access to what it just created.
			if (existing == null && uploader != null) {
				Long newProjectId = (run != null) ? run.getProject().getId() : perfRun.getProject().getId();
				membership.grantIfUserExists(newProjectId, uploader, ProjectRole.OWNER);
			}
			observation.lowCardinalityKeyValue("result", (run != null) ? run.getStatus() : "PERF_ONLY");
			if (run != null) {
				observation.highCardinalityKeyValue("run.id", String.valueOf(run.getId()));
				audit.record(uploader, "RUN_INGESTED", "API", run.getProject().getId(), "run #" + run.getId() + " "
						+ run.getStatus() + " on " + ((run.getBranch() != null) ? run.getBranch() : "-"));
			}
			if (perfRun != null) {
				audit.record(uploader, "PERF_INGESTED", "API", perfRun.getProject().getId(),
						"perf run #" + perfRun.getId());
			}
			return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponses.IngestResultJson.of(run, perfRun));
		});
	}

	private void publishGitHubStatus(TestRun run) {
		QualityGateResult gate = qualityGate.evaluate(run.getId()).orElse(null);
		Double delta = qualityGate.coverageDelta(run.getId()).orElse(null);
		gitHubStatus.publish(run, gate, delta);
		int newFailures = testRegression.diff(run.getId()).map(TestRegressionResult::newFailureCount).orElse(0);
		int slowerTests = perfRegression.diff(run.getId()).map(PerfRegressionResult::slowerCount).orElse(0);
		gitHubPrComment.publish(run, gate, delta, newFailures, slowerTests);
		gitLab.publishStatus(run, gate, delta);
		gitLab.publishMrNote(run, gate, delta, newFailures);
		gateFailureNotifier.notifyIfFailed(run, gate);
		ownerFailureNotifier.notifyOwners(run);
		alertEvents.publishForRun(run, gate);
		liveEvents.publish(run.getProject(), RunUpdate.of(run));
	}

	private void publishPerfComment(PerfRun perfRun) {
		PerfRunRegression regression = perfRunRegression.evaluate(perfRun.getId()).orElse(null);
		gitHubPrComment.publishPerf(perfRun, regression);
	}

	private static List<Supplier<InputStream>> toSuppliers(List<MultipartFile> files) {
		List<Supplier<InputStream>> suppliers = new ArrayList<>();
		if (files == null) {
			return suppliers;
		}
		for (MultipartFile file : files) {
			if (file == null || file.isEmpty()) {
				continue;
			}
			suppliers.add(() -> {
				try {
					return org.alexmond.unitrack.ingest.GzipStreams.gunzipIfNeeded(file.getInputStream());
				}
				catch (IOException ex) {
					throw new IngestException("Could not read upload '" + file.getOriginalFilename() + "'", ex);
				}
			});
		}
		return suppliers;
	}

}
