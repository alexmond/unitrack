package org.alexmond.unitrack.web.api;

import org.alexmond.unitrack.domain.PerfRun;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.ingest.IngestException;
import org.alexmond.unitrack.ingest.IngestRequest;
import org.alexmond.unitrack.ingest.IngestService;
import org.alexmond.unitrack.ingest.PerfIngestService;
import org.alexmond.unitrack.report.PerfRegressionResult;
import org.alexmond.unitrack.report.PerfRegressionService;
import org.alexmond.unitrack.report.PerfRunRegression;
import org.alexmond.unitrack.report.PerfRunRegressionService;
import org.alexmond.unitrack.report.QualityGateResult;
import org.alexmond.unitrack.report.QualityGateService;
import org.alexmond.unitrack.report.TestRegressionResult;
import org.alexmond.unitrack.report.TestRegressionService;
import org.alexmond.unitrack.web.github.GitHubPrCommentService;
import org.alexmond.unitrack.web.github.GitHubStatusService;
import org.alexmond.unitrack.web.notify.GateFailureNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

	@PostMapping(path = "/ingest", consumes = "multipart/form-data")
	public ResponseEntity<ApiResponses.IngestResultJson> ingest(@RequestParam String project,
			@RequestParam(required = false) String repoUrl, @RequestParam(required = false) String branch,
			@RequestParam(required = false) String flag, @RequestParam(required = false) String commit,
			@RequestParam(required = false) String buildUrl, @RequestParam(required = false) String ciProvider,
			@RequestParam(required = false) String runKey, @RequestParam(required = false) String baseBranch,
			@RequestParam(required = false) Integer prNumber,
			@RequestParam(name = "junit", required = false) List<MultipartFile> junit,
			@RequestParam(name = "jacoco", required = false) List<MultipartFile> jacoco,
			@RequestParam(name = "perf", required = false) List<MultipartFile> perf) {

		List<Supplier<InputStream>> junitStreams = toSuppliers(junit);
		List<Supplier<InputStream>> perfStreams = toSuppliers(perf);
		if (junitStreams.isEmpty() && perfStreams.isEmpty()) {
			throw new IngestException("Provide at least one 'junit' or 'perf' file");
		}

		IngestRequest meta = new IngestRequest(project, repoUrl, branch, flag, commit, buildUrl, ciProvider, runKey,
				baseBranch, prNumber);
		TestRun run = null;
		if (!junitStreams.isEmpty()) {
			run = ingestService.ingest(meta, junitStreams, toSuppliers(jacoco));
			publishGitHubStatus(run);
		}
		PerfRun perfRun = perfStreams.isEmpty() ? null : perfIngest.ingest(meta, perfStreams);
		if (perfRun != null) {
			publishPerfComment(perfRun);
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponses.IngestResultJson.of(run, perfRun));
	}

	private void publishGitHubStatus(TestRun run) {
		QualityGateResult gate = qualityGate.evaluate(run.getId()).orElse(null);
		Double delta = qualityGate.coverageDelta(run.getId()).orElse(null);
		gitHubStatus.publish(run, gate, delta);
		int newFailures = testRegression.diff(run.getId()).map(TestRegressionResult::newFailureCount).orElse(0);
		int slowerTests = perfRegression.diff(run.getId()).map(PerfRegressionResult::slowerCount).orElse(0);
		gitHubPrComment.publish(run, gate, delta, newFailures, slowerTests);
		gateFailureNotifier.notifyIfFailed(run, gate);
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
					return file.getInputStream();
				}
				catch (IOException ex) {
					throw new IngestException("Could not read upload '" + file.getOriginalFilename() + "'", ex);
				}
			});
		}
		return suppliers;
	}

}
