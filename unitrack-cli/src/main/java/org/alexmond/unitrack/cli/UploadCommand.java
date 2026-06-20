package org.alexmond.unitrack.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.GZIPOutputStream;

import org.springframework.core.io.ByteArrayResource;
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

	/** Server caps: 25 MB per file, 100 MB per request. */
	private static final long MAX_FILE_BYTES = 25L * 1024 * 1024;

	private static final long MAX_REQUEST_BYTES = 100L * 1024 * 1024;

	/**
	 * Per-shard target (8 MB) — small shards cross a fronting proxy/CDN most reliably.
	 * Paired with a brief pause between shards. Note a constrained free-tier CDN tunnel
	 * may still cap the total upload (~tens of MB) regardless; scope very large suites or
	 * upload coverage separately.
	 */
	private static final long SHARD_TARGET_BYTES = 8L * 1024 * 1024;

	/** Brief pause between shards so a CDN/proxy doesn't rate-limit a rapid burst. */
	private static final long SHARD_PAUSE_MS = 1000L;

	@Option(names = "--url", defaultValue = "${env:UNITRACK_URL:-http://localhost:8080}",
			description = "UniTrack server URL (env UNITRACK_URL).")
	String url;

	@Option(names = "--token", defaultValue = "${env:UNITRACK_TOKEN}",
			description = "API token, sent as a Bearer header (env UNITRACK_TOKEN).")
	String token;

	@Option(names = { "--header", "-H" },
			description = "Extra HTTP header 'Name: Value' (repeatable) — e.g. Cloudflare Access "
					+ "service-token headers when the server is behind a proxy/WAF.")
	List<String> headers = new ArrayList<>();

	@Option(names = "--project", description = "Project name (auto-detected from CI when omitted).")
	String project;

	@Option(names = "--branch", description = "Branch name.")
	String branch;

	@Option(names = "--commit", description = "Commit SHA.")
	String commit;

	@Option(names = "--build", description = "CI build URL (deep link to the job).")
	String buildUrl;

	@Option(names = "--build-name",
			description = "Friendly build identifier (e.g. CI run number); shown as 'build #N'.")
	String buildName;

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

	@Option(names = "--verbose", description = "Print the resolved request (token redacted) before sending.")
	boolean verbose;

	@Option(names = "--soft-fail", description = "Treat an upload/transport failure as a warning (exit 0).")
	boolean softFail;

	@Option(names = "--gzip", negatable = true,
			description = "Gzip each report before upload (default true) — report XML compresses ~10-20x, "
					+ "keeping large multi-module uploads under request-size limits. --no-gzip to disable.")
	boolean gzip = true;

	@Option(names = "--split-by-module",
			description = "Upload each module (the directory before /target/) as its own coverage flag/component, "
					+ "plus a merged rollup — so a multi-module project shows per-module tests, coverage and gates.")
	boolean splitByModule;

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

		if (this.splitByModule) {
			return uploadPerModule(resolvedProject, ci, junitFiles, jacocoFiles, perfFiles);
		}
		return uploadFileSet(buildFields(resolvedProject, ci), junitFiles, jacocoFiles, perfFiles);
	}

	/**
	 * Gzips (if enabled), enforces the per-file cap, shards on the request-size limit,
	 * and POSTs one logical run described by {@code fields} (its
	 * {@code flag}/{@code runKey} already set).
	 */
	private Integer uploadFileSet(Map<String, String> fields, List<Resource> junitFiles, List<Resource> jacocoFiles,
			List<Resource> perfFiles) {
		// Gzip each report (the server transparently inflates). Done before the size
		// check so the
		// limits apply to what is actually sent — a large but compressible upload fits.
		List<Resource> junitOut = this.gzip ? gzipAll(junitFiles) : junitFiles;
		List<Resource> jacocoOut = this.gzip ? gzipAll(jacocoFiles) : jacocoFiles;
		List<Resource> perfOut = this.gzip ? gzipAll(perfFiles) : perfFiles;
		Map<String, List<Resource>> files = new LinkedHashMap<>();
		files.put("junit", junitOut);
		files.put("jacoco", jacocoOut);
		files.put("perf", perfOut);

		List<Resource> allFiles = new ArrayList<>(junitOut);
		allFiles.addAll(jacocoOut);
		allFiles.addAll(perfOut);
		// A single file over the per-file cap can't be split — hard fail.
		String tooBig = perFileError(allFiles, MAX_FILE_BYTES);
		if (tooBig != null) {
			System.err.println("error: " + tooBig + ".");
			return ExitCodes.REJECTED;
		}
		if (this.verbose) {
			printVerbose(fields, allFiles);
		}

		if (this.dryRun) {
			System.out.println("[dry-run] would POST to " + this.url + "/api/v1/ingest");
			System.out.println("[dry-run] fields: " + fields);
			return ExitCodes.OK;
		}

		// Too big for one request (even gzipped) → split into shards the server merges by
		// run key.
		List<Map<String, List<Resource>>> batches = splitIntoBatches(files, SHARD_TARGET_BYTES);
		if (batches.size() > 1) {
			if (blankToNull(fields.get("runKey")) == null) {
				fields.put("runKey", "split-" + System.nanoTime());
			}
			System.out.printf("Upload exceeds %d MB — splitting into %d requests (run key '%s'); merged server-side.%n",
					mb(SHARD_TARGET_BYTES), batches.size(), fields.get("runKey"));
		}
		return upload(batches, fields);
	}

	/**
	 * Uploads each module (grouped by the directory before {@code /target/}) as its own
	 * coverage flag, then a merged rollup under the default flag. The rollup is uploaded
	 * LAST so it is the project's latest run — keeping the dashboard headline
	 * whole-project while each module appears as a component with its own tests, coverage
	 * and gate. A distinct run key per component stops the server merging them into one
	 * run.
	 */
	private Integer uploadPerModule(String project, CiMetadata ci, List<Resource> junit, List<Resource> jacoco,
			List<Resource> perf) {
		Map<String, ModuleGroup> byModule = groupByModule(junit, jacoco, perf);
		if (byModule.size() <= 1) {
			System.out.println("Only one module detected — uploading as a single run.");
			return uploadFileSet(buildFields(project, ci), junit, jacoco, perf);
		}
		String base = coalesce(this.runKey, ci.runKey());
		System.out.printf("Splitting into %d module component(s) + a rollup.%n", byModule.size());
		int worst = ExitCodes.OK;
		for (Map.Entry<String, ModuleGroup> e : byModule.entrySet()) {
			String module = e.getKey();
			ModuleGroup g = e.getValue();
			Map<String, String> fields = buildFields(project, ci);
			fields.put("flag", module);
			fields.put("runKey", (base != null) ? base + "::" + module : null);
			System.out.printf("→ module '%s'%n", module);
			worst = Math.max(worst, uploadFileSet(fields, g.junit(), g.jacoco(), g.perf()));
		}
		Map<String, String> rollup = buildFields(project, ci);
		rollup.put("runKey", (base != null) ? base + "::rollup" : null);
		System.out.println("→ rollup (all modules)");
		return Math.max(worst, uploadFileSet(rollup, junit, jacoco, perf));
	}

	private static Map<String, ModuleGroup> groupByModule(List<Resource> junit, List<Resource> jacoco,
			List<Resource> perf) {
		Map<String, List<Resource>[]> acc = new java.util.TreeMap<>();
		index(acc, junit, 0);
		index(acc, jacoco, 1);
		index(acc, perf, 2);
		Map<String, ModuleGroup> out = new java.util.TreeMap<>();
		acc.forEach((module, lists) -> out.put(module, new ModuleGroup(lists[0], lists[1], lists[2])));
		return out;
	}

	@SuppressWarnings("unchecked")
	private static void index(Map<String, List<Resource>[]> acc, List<Resource> files, int slot) {
		for (Resource r : files) {
			List<Resource>[] lists = acc.computeIfAbsent(moduleOf(r),
					(k) -> new List[] { new ArrayList<>(), new ArrayList<>(), new ArrayList<>() });
			lists[slot].add(r);
		}
	}

	/**
	 * The module name for a report: the path segment immediately before {@code /target/}.
	 */
	static String moduleOf(Resource r) {
		String path;
		try {
			path = r.getURI().getPath();
		}
		catch (IOException | RuntimeException ex) {
			path = r.getFilename();
		}
		if (path == null) {
			return "(root)";
		}
		path = path.replace('\\', '/');
		int target = path.indexOf("/target/");
		if (target < 0) {
			return "(root)";
		}
		String beforeTarget = path.substring(0, target);
		int slash = beforeTarget.lastIndexOf('/');
		String module = (slash >= 0) ? beforeTarget.substring(slash + 1) : beforeTarget;
		return module.isBlank() ? "(root)" : module;
	}

	/**
	 * POSTs each shard (a single batch in the common case), reporting the run and
	 * honouring --soft-fail.
	 */
	private Integer upload(List<Map<String, List<Resource>>> batches, Map<String, String> fields) {
		try {
			IngestResponse response = null;
			for (int i = 0; i < batches.size(); i++) {
				if (i > 0) {
					pauseBetweenShards();
				}
				IngestResponse r = this.client.ingest(this.url, this.token, UploadClient.parseHeaders(this.headers),
						fields, batches.get(i));
				response = (response != null) ? response : r;
				if (batches.size() > 1) {
					System.out.printf("  shard %d/%d uploaded.%n", i + 1, batches.size());
				}
			}
			if (response != null && response.runId() != null) {
				System.out.printf("Uploaded run #%d -> %s/runs/%d%n", response.runId(), this.url, response.runId());
			}
			else {
				System.out.println("Upload accepted.");
			}
			return ExitCodes.OK;
		}
		catch (UploadException ex) {
			if (this.softFail && (ex.exitCode() == ExitCodes.TRANSPORT || ex.exitCode() == ExitCodes.REJECTED)) {
				System.out.println("warning: upload failed (" + ex.getMessage() + ") — continuing due to --soft-fail.");
				return ExitCodes.OK;
			}
			System.err.println("error: " + ex.getMessage());
			return ex.exitCode();
		}
	}

	private void printVerbose(Map<String, String> fields, List<Resource> files) {
		System.out.println("[verbose] POST " + this.url + "/api/v1/ingest");
		System.out.println("[verbose] token: " + (notBlank(this.token) ? "***" : "(none)"));
		System.out.println("[verbose] fields: " + fields);
		for (Resource r : files) {
			System.out.println("[verbose] file: " + nameOf(r) + " (" + sizeOf(r) + " bytes)");
		}
	}

	/**
	 * Returns an error if any single file exceeds {@code maxFile} (a single file can't be
	 * split); else null. A too-large total is handled by sharding, not rejected.
	 */
	static String perFileError(List<Resource> files, long maxFile) {
		for (Resource r : files) {
			long len = sizeOf(r);
			if (len > maxFile) {
				return nameOf(r) + " is " + mb(len) + " MB (max " + mb(maxFile) + " MB per file; can't be split)";
			}
		}
		return null;
	}

	/**
	 * Packs the report files into batches each within {@code target} bytes, so a payload
	 * too large for one request (even gzipped) is sharded — the server merges the shards
	 * by run key. Returns the single map unchanged when it already fits. Every batch
	 * keeps at least one junit/perf part (the server rejects coverage-only requests);
	 * coverage rides along.
	 */
	static List<Map<String, List<Resource>>> splitIntoBatches(Map<String, List<Resource>> files, long target) {
		List<Part> anchors = new ArrayList<>();
		List<Part> riders = new ArrayList<>();
		long total = 0;
		for (Map.Entry<String, List<Resource>> entry : files.entrySet()) {
			for (Resource r : entry.getValue()) {
				Part part = new Part(entry.getKey(), r, sizeOf(r));
				total += part.size();
				("jacoco".equals(entry.getKey()) ? riders : anchors).add(part);
			}
		}
		if (total <= target) {
			return List.of(files);
		}
		// First-fit-decreasing: anchors first (so every bin has a junit/perf part), then
		// riders.
		anchors.sort((a, b) -> Long.compare(b.size(), a.size()));
		List<List<Part>> bins = new ArrayList<>();
		List<Long> sizes = new ArrayList<>();
		for (Part p : anchors) {
			place(bins, sizes, p, target);
		}
		for (Part p : riders) {
			place(bins, sizes, p, target);
		}
		List<Map<String, List<Resource>>> batches = new ArrayList<>();
		for (List<Part> bin : bins) {
			Map<String, List<Resource>> batch = new LinkedHashMap<>();
			for (Part p : bin) {
				batch.computeIfAbsent(p.field(), (k) -> new ArrayList<>()).add(p.resource());
			}
			batches.add(batch);
		}
		return batches;
	}

	private static void place(List<List<Part>> bins, List<Long> sizes, Part part, long target) {
		for (int i = 0; i < bins.size(); i++) {
			if (sizes.get(i) + part.size() <= target) {
				bins.get(i).add(part);
				sizes.set(i, sizes.get(i) + part.size());
				return;
			}
		}
		bins.add(new ArrayList<>(List.of(part)));
		sizes.add(part.size());
	}

	private static long sizeOf(Resource r) {
		try {
			return r.contentLength();
		}
		catch (IOException ex) {
			return 0;
		}
	}

	private static String nameOf(Resource r) {
		String name = r.getFilename();
		return (name != null) ? name : "file";
	}

	private static long mb(long bytes) {
		return bytes / (1024 * 1024);
	}

	private static boolean notBlank(String s) {
		return s != null && !s.isBlank();
	}

	private static String coalesce(String explicit, String detected) {
		return (explicit != null && !explicit.isBlank()) ? explicit : detected;
	}

	private static String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s;
	}

	private Map<String, String> buildFields(String project, CiMetadata ci) {
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("project", project);
		fields.put("branch", coalesce(this.branch, ci.branch()));
		fields.put("commit", coalesce(this.commit, ci.commit()));
		fields.put("buildUrl", coalesce(this.buildUrl, ci.buildUrl()));
		fields.put("buildName", coalesce(this.buildName, ci.buildName()));
		fields.put("repoUrl", coalesce(this.repoUrl, ci.repoUrl()));
		fields.put("flag", this.flag);
		fields.put("runKey", coalesce(this.runKey, ci.runKey()));
		fields.put("ciProvider", coalesce(this.ciProvider, ci.ciProvider()));
		return fields;
	}

	private static void pauseBetweenShards() {
		try {
			Thread.sleep(SHARD_PAUSE_MS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static List<Resource> gzipAll(List<Resource> files) {
		List<Resource> out = new ArrayList<>(files.size());
		for (Resource file : files) {
			out.add(gzip(file));
		}
		return out;
	}

	/** Gzips a resource into an in-memory part, preserving its filename. */
	private static Resource gzip(Resource source) {
		String filename = source.getFilename();
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (InputStream in = source.getInputStream(); GZIPOutputStream out = new GZIPOutputStream(buffer)) {
			in.transferTo(out);
		}
		catch (IOException ex) {
			throw new UploadException(ExitCodes.REJECTED, "Could not gzip " + filename, ex);
		}
		return new ByteArrayResource(buffer.toByteArray()) {
			@Override
			public String getFilename() {
				return filename;
			}
		};
	}

	/**
	 * A single multipart part: its form field
	 * ({@code junit}/{@code jacoco}/{@code perf}), resource and size.
	 */
	/** Resolved reports grouped by the module they belong to. */
	private record ModuleGroup(List<Resource> junit, List<Resource> jacoco, List<Resource> perf) {
	}

	private record Part(String field, Resource resource, long size) {
	}

}
