package org.alexmond.unitrack.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** {@code unitrack upload} — push JUnit/coverage/perf reports to a UniTrack server. */
@Component
@Command(name = "upload", mixinStandardHelpOptions = true,
		description = "Upload JUnit, coverage and performance reports to UniTrack.")
@SuppressWarnings("PMD.SystemPrintln") // a CLI legitimately writes user-facing output to
										// stdout/stderr
class UploadCommand implements Callable<Integer> {

	@Option(names = "--url", defaultValue = "${env:UNITRACK_URL:-http://localhost:8080}",
			description = "UniTrack server URL (env UNITRACK_URL).")
	String url;

	@Option(names = "--token", defaultValue = "${env:UNITRACK_TOKEN}",
			description = "API token, sent as a Bearer header (env UNITRACK_TOKEN).")
	String token;

	@Option(names = "--project", description = "Project name (auto-detected from CI when omitted).")
	String project;

	@Option(names = "--branch", description = "Branch name.")
	String branch;

	@Option(names = "--commit", description = "Commit SHA.")
	String commit;

	@Option(names = "--build", description = "CI build URL (deep link to the job).")
	String buildUrl;

	@Option(names = "--repo", description = "Repository URL.")
	String repoUrl;

	@Option(names = "--flag", description = "Coverage flag / component.")
	String flag;

	@Option(names = "--run-key", description = "Run key to merge sharded uploads into one run.")
	String runKey;

	@Option(names = "--ci", description = "CI provider id.")
	String ciProvider;

	@Option(names = "--junit", description = "JUnit/Surefire XML glob (repeatable).")
	List<String> junit = new ArrayList<>();

	@Option(names = "--jacoco", description = "Coverage report glob — JaCoCo/Cobertura/LCOV/OpenCover (repeatable).")
	List<String> jacoco = new ArrayList<>();

	@Option(names = "--perf", description = "Performance result glob — JMeter JTL / k6 JSON (repeatable).")
	List<String> perf = new ArrayList<>();

	@Option(names = "--dry-run", description = "Resolve files and print what would be sent, without uploading.")
	boolean dryRun;

	@Option(names = "--allow-empty", description = "Allow an upload when no report files matched.")
	boolean allowEmpty;

	private final UploadClient client;

	private final ReportResolver resolver;

	private final CiMetadataDetector detector;

	UploadCommand(UploadClient client, ReportResolver resolver, CiMetadataDetector detector) {
		this.client = client;
		this.resolver = resolver;
		this.detector = detector;
	}

	@Override
	public Integer call() {
		List<Resource> junitFiles = this.resolver.resolve(this.junit);
		List<Resource> jacocoFiles = this.resolver.resolve(this.jacoco);
		List<Resource> perfFiles = this.resolver.resolve(this.perf);
		int total = junitFiles.size() + jacocoFiles.size() + perfFiles.size();
		System.out.printf("Resolved %d junit, %d jacoco, %d perf file(s).%n", junitFiles.size(), jacocoFiles.size(),
				perfFiles.size());
		if (total == 0 && !this.allowEmpty) {
			System.err.println(
					"error: no report files matched the given globs " + "(use --allow-empty to upload metadata only).");
			return ExitCodes.USAGE;
		}

		// Explicit flags win; anything omitted is filled from the detected CI
		// environment.
		CiMetadata ci = this.detector.detect();
		String resolvedProject = coalesce(this.project, ci.project());
		if (resolvedProject == null || resolvedProject.isBlank()) {
			System.err.println("error: --project is required (and could not be detected from the CI environment).");
			return ExitCodes.USAGE;
		}
		if (ci.ciProvider() != null) {
			System.out.println("Detected CI: " + ci.ciProvider());
		}

		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("project", resolvedProject);
		fields.put("branch", coalesce(this.branch, ci.branch()));
		fields.put("commit", coalesce(this.commit, ci.commit()));
		fields.put("buildUrl", coalesce(this.buildUrl, ci.buildUrl()));
		fields.put("repoUrl", coalesce(this.repoUrl, ci.repoUrl()));
		fields.put("flag", this.flag);
		fields.put("runKey", coalesce(this.runKey, ci.runKey()));
		fields.put("ciProvider", coalesce(this.ciProvider, ci.ciProvider()));
		Map<String, List<Resource>> files = new LinkedHashMap<>();
		files.put("junit", junitFiles);
		files.put("jacoco", jacocoFiles);
		files.put("perf", perfFiles);

		if (this.dryRun) {
			System.out.println("[dry-run] would POST to " + this.url + "/api/v1/ingest");
			System.out.println("[dry-run] fields: " + fields);
			return ExitCodes.OK;
		}

		try {
			IngestResponse response = this.client.ingest(this.url, this.token, fields, files);
			if (response.runId() != null) {
				System.out.printf("Uploaded run #%d -> %s/runs/%d%n", response.runId(), this.url, response.runId());
			}
			else {
				System.out.println("Upload accepted.");
			}
			return ExitCodes.OK;
		}
		catch (UploadException ex) {
			System.err.println("error: " + ex.getMessage());
			return ex.exitCode();
		}
	}

	private static String coalesce(String explicit, String detected) {
		return (explicit != null && !explicit.isBlank()) ? explicit : detected;
	}

}
