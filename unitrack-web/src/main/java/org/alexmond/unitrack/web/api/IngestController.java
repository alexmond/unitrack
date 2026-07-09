package org.alexmond.unitrack.web.api;

import org.alexmond.unitrack.domain.PerfRun;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.ProjectRole;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.ingest.BoundedInputStream;
import org.alexmond.unitrack.ingest.GzipStreams;
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
import org.alexmond.unitrack.web.github.GitHubCheckRunService;
import org.alexmond.unitrack.web.github.GitHubPrCommentService;
import org.alexmond.unitrack.web.github.GitHubStatusService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.alexmond.unitrack.web.alert.AlertEventPublisher;
import org.alexmond.unitrack.web.ingest.IngestJobService;
import org.alexmond.unitrack.web.ingest.IngestProperties;
import org.alexmond.unitrack.web.live.LiveEventService;
import org.alexmond.unitrack.web.live.RunUpdate;
import org.alexmond.unitrack.web.notify.GateFailureNotifier;
import org.alexmond.unitrack.web.notify.OwnerFailureNotifier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
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
 *
 * <p>
 * Synchronous by default — the response carries the parsed result the CLI/Action rely on
 * for the quality-gate exit code and PR comment. Pass {@code async=true} to enqueue
 * instead: the upload is buffered, a job is queued to a bounded worker pool, and the
 * response is {@code 202} + the job id to poll at {@code /api/v1/ingest-jobs/{id}}
 * (#368).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class IngestController {

	private static final Logger log = LoggerFactory.getLogger(IngestController.class);

	private final IngestService ingestService;

	private final QualityGateService qualityGate;

	private final GitHubStatusService gitHubStatus;

	private final GitHubCheckRunService gitHubCheckRun;

	private final GitHubPrCommentService gitHubPrComment;

	private final TestRegressionService testRegression;

	private final PerfRegressionService perfRegression;

	private final PerfIngestService perfIngest;

	private final IngestJobService ingestJobs;

	private final IngestProperties ingestProperties;

	@Qualifier("ingestExecutor")
	private final ThreadPoolTaskExecutor ingestExecutor;

	private final PerfRunRegressionService perfRunRegression;

	private final GateFailureNotifier gateFailureNotifier;

	private final OwnerFailureNotifier ownerFailureNotifier;

	private final AlertEventPublisher alertEvents;

	private final LiveEventService liveEvents;

	private final ReportingService reporting;

	private final MembershipService membership;

	private final ProjectAccessService access;

	private final ObservationRegistry observationRegistry;

	private final AuditService audit;

	private final org.alexmond.unitrack.web.gitlab.GitLabService gitLab;

	@PostMapping(path = "/ingest", consumes = "multipart/form-data")
	public ResponseEntity<?> ingest(@RequestParam String project, @RequestParam(required = false) String repoUrl,
			@RequestParam(required = false) String branch, @RequestParam(required = false) String flag,
			@RequestParam(required = false) String commit, @RequestParam(required = false) String buildUrl,
			@RequestParam(required = false) String buildName, @RequestParam(required = false) String ciProvider,
			@RequestParam(required = false) String runKey, @RequestParam(required = false) String baseBranch,
			@RequestParam(required = false) Integer prNumber, @RequestParam(required = false) String module,
			@RequestParam(name = "junit", required = false) List<MultipartFile> junit,
			@RequestParam(name = "jacoco", required = false) List<MultipartFile> jacoco,
			@RequestParam(name = "perf", required = false) List<MultipartFile> perf,
			@RequestParam(name = "perfFlag", required = false) List<String> perfFlag,
			@RequestParam(name = "sourceManifest", required = false) MultipartFile sourceManifest,
			@RequestParam(name = "async", defaultValue = "false") boolean async) {

		boolean hasJunit = nonEmpty(junit);
		boolean hasPerf = nonEmpty(perf);
		if (!hasJunit && !hasPerf) {
			throw new IngestException("Provide at least one 'junit' or 'perf' file");
		}
		String kind = hasJunit ? (hasPerf ? "tests+perf" : "tests") : "perf";
		long size = totalSize(junit, jacoco, perf);

		// Authorize against the existing project; a brand-new project will be created by
		// ingest. Done on the request thread so the SecurityContext is available.
		String uploader = access.currentUsername();
		Project existing = reporting.findProjectByName(project).orElse(null);
		if (existing != null && uploader != null && !membership.canWrite(uploader, existing.getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Write access to project required");
		}
		boolean newProject = (existing == null);

		// Read the source manifest (git ls-files) up front — it's small text and `meta`
		// flows in memory through the async path, so no need to spool it.
		List<String> manifest = readManifest(sourceManifest);
		IngestRequest meta = new IngestRequest(project, repoUrl, branch, flag, commit, buildUrl, buildName, ciProvider,
				runKey, baseBranch, prNumber, module, manifest);
		long reportLimit = ingestProperties.maxReportBytesValue();
		long perfLimit = ingestProperties.maxPerfBytesValue();

		// Each non-empty perf file is its own series; resolve its flag (explicit
		// perfFlag,
		// else the filename when several share one upload, else the request flag).
		List<MultipartFile> perfFiles = nonEmptyFiles(perf);
		List<String> perfFlags = resolvePerfFlags(perfFiles, perfFlag, flag);

		if (async) {
			return enqueueAsync(meta, project, branch, commit, kind, size, uploader, newProject, junit, jacoco,
					perfFiles, perfFlags, reportLimit, perfLimit);
		}

		// Synchronous (default): preserves the gate/exit-code contract — the parsed
		// result is returned in the response.
		List<Supplier<InputStream>> junitStreams = toSuppliers(junit, reportLimit, "report");
		List<Supplier<InputStream>> jacocoStreams = toSuppliers(jacoco, reportLimit, "report");
		List<PerfIngestService.PerfPart> perfParts = new ArrayList<>();
		for (int i = 0; i < perfFiles.size(); i++) {
			perfParts.add(
					new PerfIngestService.PerfPart(supplierFor(perfFiles.get(i), perfLimit, "perf"), perfFlags.get(i)));
		}
		Long jobId = ingestJobs.start(project, branch, commit, kind, size, uploader);
		ApiResponses.IngestResultJson body = process(meta, project, kind, junitStreams, jacocoStreams, perfParts,
				uploader, newProject, jobId);
		return ResponseEntity.status(HttpStatus.CREATED).body(body);
	}

	private ResponseEntity<AsyncAcceptedJson> enqueueAsync(IngestRequest meta, String project, String branch,
			String commit, String kind, long size, String uploader, boolean newProject, List<MultipartFile> junit,
			List<MultipartFile> jacoco, List<MultipartFile> perfFiles, List<String> perfFlags, long reportLimit,
			long perfLimit) {
		// The multipart streams die with the request, so buffer the (decompressed,
		// size-guarded) parts to temp files before returning. An oversized upload trips
		// the
		// guard here and fails fast with 4xx, before anything is queued.
		Spool spool = spoolToTemp(junit, jacoco, perfFiles, perfFlags, reportLimit, perfLimit);
		Long jobId = ingestJobs.enqueue(project, branch, commit, kind, size, uploader);
		try {
			ingestExecutor.execute(() -> runAsync(meta, project, kind, uploader, newProject, jobId, spool));
		}
		catch (RejectedExecutionException ex) {
			spool.cleanup();
			ingestJobs.failed(jobId, "Ingest queue is full — retry later");
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Ingest queue is full — retry later", ex);
		}
		return ResponseEntity.accepted().body(new AsyncAcceptedJson(jobId, "QUEUED", "/api/v1/ingest-jobs/" + jobId));
	}

	private void runAsync(IngestRequest meta, String project, String kind, String uploader, boolean newProject,
			Long jobId, Spool spool) {
		try {
			ingestJobs.markProcessing(jobId);
			process(meta, project, kind, spool.junit(), spool.jacoco(), spool.perf(), uploader, newProject, jobId);
		}
		catch (RuntimeException ex) {
			// The job is already recorded FAILED with the reason inside process(); just
			// note it server-side. Nothing to return — this ran off the request thread.
			log.warn("Async ingest job {} failed: {}", jobId, ex.getMessage());
		}
		finally {
			spool.cleanup();
		}
	}

	/**
	 * Runs the ingest unit of work and settles the job (PROCESSED or FAILED). Shared by
	 * the sync and async paths; the job is expected to already exist in PROCESSING.
	 */
	private ApiResponses.IngestResultJson process(IngestRequest meta, String project, String kind,
			List<Supplier<InputStream>> junitStreams, List<Supplier<InputStream>> jacocoStreams,
			List<PerfIngestService.PerfPart> perfParts, String uploader, boolean newProject, Long jobId) {
		// One Observation around the unit of work: Boot derives the metric (Timer) and
		// the
		// span from it. Low-cardinality dimensions become metric tags; ids/names are
		// trace-only (high-cardinality) to avoid metric tag explosion.
		Observation observation = Observation.createNotStarted("unitrack.ingest", observationRegistry)
			.lowCardinalityKeyValue("kind", kind)
			.lowCardinalityKeyValue("has_coverage", String.valueOf(!jacocoStreams.isEmpty()))
			.highCardinalityKeyValue("project", project);
		try {
			return observation.observe(() -> {
				TestRun run = null;
				if (!junitStreams.isEmpty()) {
					run = ingestService.ingest(meta, junitStreams, jacocoStreams);
					// Post-ingest publishing as an explicit child span — parent passed
					// through (not via thread-local), so it nests correctly off-thread.
					TestRun ingested = run;
					Observation.createNotStarted("unitrack.report", observationRegistry)
						.parentObservation(observation)
						.observe(() -> publishGitHubStatus(ingested));
				}
				// Each perf file is its own series; the first is the "primary" returned
				// in
				// the response and recorded on the job.
				List<PerfRun> perfRunList = perfParts.isEmpty() ? List.of() : perfIngest.ingestAll(meta, perfParts);
				PerfRun perfRun = perfRunList.isEmpty() ? null : perfRunList.get(0);
				for (PerfRun pr : perfRunList) {
					publishPerfComment(pr);
				}
				// A newly-created (PRIVATE by default) project gets its uploader as
				// OWNER, so an authenticated CI/user keeps access to what it just
				// created.
				if (newProject && uploader != null) {
					Long newProjectId = (run != null) ? run.getProject().getId() : perfRun.getProject().getId();
					membership.grantIfUserExists(newProjectId, uploader, ProjectRole.OWNER);
				}
				observation.lowCardinalityKeyValue("result", (run != null) ? run.getStatus() : "PERF_ONLY");
				if (run != null) {
					observation.highCardinalityKeyValue("run.id", String.valueOf(run.getId()));
					audit.record(uploader, "RUN_INGESTED", "API", run.getProject().getId(), "run #" + run.getId() + " "
							+ run.getStatus() + " on " + ((run.getBranch() != null) ? run.getBranch() : "-"));
				}
				for (PerfRun pr : perfRunList) {
					audit.record(uploader, "PERF_INGESTED", "API", pr.getProject().getId(),
							"perf run #" + pr.getId() + " (" + pr.getFlag() + ")");
				}
				ingestJobs.succeeded(jobId, (run != null) ? run.getProject().getId() : perfRun.getProject().getId(),
						(run != null) ? run.getId() : null, (perfRun != null) ? perfRun.getId() : null);
				return ApiResponses.IngestResultJson.of(run, perfRun);
			});
		}
		catch (RuntimeException ex) {
			ingestJobs.failed(jobId, (ex.getMessage() != null) ? ex.getMessage() : ex.getClass().getSimpleName());
			throw ex;
		}
	}

	private static boolean nonEmpty(List<MultipartFile> files) {
		if (files == null) {
			return false;
		}
		for (MultipartFile file : files) {
			if (file != null && !file.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Cap on manifest entries — a huge checkout adds no path-resolution value past this.
	 */
	private static final int MAX_MANIFEST_ENTRIES = 50_000;

	/**
	 * Parse the newline-delimited source manifest ({@code git ls-files}) into
	 * repo-relative paths, or an empty list when absent/unreadable. Best-effort: a
	 * manifest we can't read must never fail the ingest (coverage links just fall back to
	 * package-relative).
	 */
	private static List<String> readManifest(MultipartFile manifest) {
		if (manifest == null || manifest.isEmpty()) {
			return List.of();
		}
		List<String> paths = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(manifest.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null && paths.size() < MAX_MANIFEST_ENTRIES) {
				String trimmed = line.trim();
				if (!trimmed.isEmpty()) {
					paths.add(trimmed);
				}
			}
		}
		catch (IOException ex) {
			log.warn("Could not read source manifest ({} bytes): {}", manifest.getSize(), ex.getMessage());
			return List.of();
		}
		return paths;
	}

	/** Total uploaded bytes across all parts, for the ingest-job record. */
	private static long totalSize(List<MultipartFile> junit, List<MultipartFile> jacoco, List<MultipartFile> perf) {
		long total = 0;
		for (List<MultipartFile> group : java.util.Arrays.asList(junit, jacoco, perf)) {
			if (group != null) {
				for (MultipartFile file : group) {
					total += file.getSize();
				}
			}
		}
		return total;
	}

	private void publishGitHubStatus(TestRun run) {
		QualityGateResult gate = qualityGate.evaluate(run.getId()).orElse(null);
		Double delta = qualityGate.coverageDelta(run.getId()).orElse(null);
		int newFailures = testRegression.diff(run.getId()).map(TestRegressionResult::newFailureCount).orElse(0);
		int slowerTests = perfRegression.diff(run.getId()).map(PerfRegressionResult::slowerCount).orElse(0);
		// App deployments get a rich check run (summary + inline PR annotations);
		// PAT-only
		// deployments fall back to the classic commit status.
		if (!gitHubCheckRun.publish(run, gate, delta, newFailures)) {
			gitHubStatus.publish(run, gate, delta);
		}
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

	private static List<Supplier<InputStream>> toSuppliers(List<MultipartFile> files, long maxBytes, String label) {
		List<Supplier<InputStream>> suppliers = new ArrayList<>();
		if (files == null) {
			return suppliers;
		}
		for (MultipartFile file : files) {
			if (file == null || file.isEmpty()) {
				continue;
			}
			suppliers.add(supplierFor(file, maxBytes, label));
		}
		return suppliers;
	}

	private static Supplier<InputStream> supplierFor(MultipartFile file, long maxBytes, String label) {
		return () -> {
			try {
				// Guard the DECOMPRESSED stream: gzip inflates ~10-20x, so the size cap
				// must
				// count bytes the parser actually consumes, not the wire size.
				InputStream inflated = GzipStreams.gunzipIfNeeded(file.getInputStream());
				return new BoundedInputStream(inflated, maxBytes, label);
			}
			catch (IOException ex) {
				throw new IngestException("Could not read upload '" + file.getOriginalFilename() + "'", ex);
			}
		};
	}

	private static List<MultipartFile> nonEmptyFiles(List<MultipartFile> files) {
		List<MultipartFile> kept = new ArrayList<>();
		if (files != null) {
			for (MultipartFile file : files) {
				if (file != null && !file.isEmpty()) {
					kept.add(file);
				}
			}
		}
		return kept;
	}

	/**
	 * The flag (series) for each perf file: an explicit {@code perfFlag} param if given,
	 * else the filename stem when several perf files share one upload (so each is its own
	 * series), else the request-level {@code flag} (single-file back-compat).
	 */
	private static List<String> resolvePerfFlags(List<MultipartFile> perfFiles, List<String> perfFlag,
			String requestFlag) {
		List<String> flags = new ArrayList<>();
		boolean multiple = perfFiles.size() > 1;
		for (int i = 0; i < perfFiles.size(); i++) {
			String explicit = (perfFlag != null && i < perfFlag.size()) ? perfFlag.get(i) : null;
			if (explicit != null && !explicit.isBlank()) {
				flags.add(explicit.trim());
			}
			else {
				flags.add(multiple ? filenameStem(perfFiles.get(i)) : requestFlag);
			}
		}
		return flags;
	}

	/** {@code dir/api-load.jtl.gz} → {@code api-load}; null/blank → {@code default}. */
	private static String filenameStem(MultipartFile file) {
		String name = file.getOriginalFilename();
		if (name == null || name.isBlank()) {
			return "default";
		}
		int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
		if (slash >= 0) {
			name = name.substring(slash + 1);
		}
		int dot = name.indexOf('.');
		if (dot > 0) {
			name = name.substring(0, dot);
		}
		return name.isBlank() ? "default" : name;
	}

	/**
	 * Buffers each (decompressed, size-guarded) uploaded part to a temp file so the async
	 * worker can read it after the request has returned. Partial temp files are cleaned
	 * up if any part is rejected.
	 */
	private Spool spoolToTemp(List<MultipartFile> junit, List<MultipartFile> jacoco, List<MultipartFile> perfFiles,
			List<String> perfFlags, long reportLimit, long perfLimit) {
		List<Path> temps = new ArrayList<>();
		try {
			List<Supplier<InputStream>> junitStreams = spoolGroup(junit, reportLimit, "report", temps);
			List<Supplier<InputStream>> jacocoStreams = spoolGroup(jacoco, reportLimit, "report", temps);
			List<PerfIngestService.PerfPart> perfParts = new ArrayList<>();
			for (int i = 0; i < perfFiles.size(); i++) {
				MultipartFile file = perfFiles.get(i);
				Path tmp = createTempCopy(file, perfLimit, "perf");
				temps.add(tmp);
				String name = file.getOriginalFilename();
				perfParts.add(new PerfIngestService.PerfPart(() -> openTemp(tmp, name), perfFlags.get(i)));
			}
			return new Spool(junitStreams, jacocoStreams, perfParts, temps);
		}
		catch (RuntimeException ex) {
			deleteQuietly(temps);
			throw ex;
		}
	}

	private List<Supplier<InputStream>> spoolGroup(List<MultipartFile> files, long maxBytes, String label,
			List<Path> temps) {
		List<Supplier<InputStream>> suppliers = new ArrayList<>();
		if (files == null) {
			return suppliers;
		}
		for (MultipartFile file : files) {
			if (file == null || file.isEmpty()) {
				continue;
			}
			Path tmp = createTempCopy(file, maxBytes, label);
			temps.add(tmp);
			String name = file.getOriginalFilename();
			suppliers.add(() -> openTemp(tmp, name));
		}
		return suppliers;
	}

	private static Path createTempCopy(MultipartFile file, long maxBytes, String label) {
		try {
			Path tmp = createOwnerOnlyTempFile();
			try (InputStream in = new BoundedInputStream(GzipStreams.gunzipIfNeeded(file.getInputStream()), maxBytes,
					label); OutputStream out = Files.newOutputStream(tmp)) {
				in.transferTo(out);
			}
			return tmp;
		}
		catch (IOException ex) {
			throw new IngestException(
					"Could not buffer upload '" + file.getOriginalFilename() + "': " + ex.getMessage(), ex);
		}
	}

	/**
	 * Creates the ingest scratch file with owner-only permissions from the start, so it
	 * is never briefly world-readable in the shared temp dir (defense-in-depth on
	 * multi-tenant hosts). Falls back to a plain temp file on non-POSIX filesystems.
	 */
	private static Path createOwnerOnlyTempFile() throws IOException {
		try {
			return Files.createTempFile("unitrack-ingest-", ".bin",
					PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
		}
		catch (UnsupportedOperationException ex) {
			return Files.createTempFile("unitrack-ingest-", ".bin");
		}
	}

	private static InputStream openTemp(Path tmp, String name) {
		try {
			return new BufferedInputStream(Files.newInputStream(tmp));
		}
		catch (IOException ex) {
			throw new IngestException("Could not read buffered upload '" + name + "'", ex);
		}
	}

	private static void deleteQuietly(List<Path> temps) {
		for (Path tmp : temps) {
			try {
				Files.deleteIfExists(tmp);
			}
			catch (IOException ex) {
				log.debug("Could not delete ingest temp file {}: {}", tmp, ex.getMessage());
			}
		}
	}

	/**
	 * Buffered async upload: temp-file-backed suppliers/parts plus the paths to clean up.
	 */
	private record Spool(List<Supplier<InputStream>> junit, List<Supplier<InputStream>> jacoco,
			List<PerfIngestService.PerfPart> perf, List<Path> temps) {

		void cleanup() {
			deleteQuietly(this.temps);
		}

	}

	/** 202 response for an accepted async upload. */
	public record AsyncAcceptedJson(Long jobId, String status, String statusUrl) {
	}

}
