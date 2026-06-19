package org.alexmond.unitrack.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code unitrack gate} — fail the build (exit 1) when a project's latest run did not
 * pass the gate.
 */
@Component
@Command(name = "gate", mixinStandardHelpOptions = true,
		description = "Check a project's quality gate; exit 1 if it failed.")
@SuppressWarnings("PMD.SystemPrintln") // a CLI legitimately writes user-facing output to
										// stdout/stderr
class GateCommand implements Callable<Integer> {

	@Option(names = "--url", defaultValue = "${env:UNITRACK_URL:-http://localhost:8080}",
			description = "UniTrack server URL (env UNITRACK_URL).")
	String url;

	@Option(names = "--token", defaultValue = "${env:UNITRACK_TOKEN}", description = "API token (env UNITRACK_TOKEN).")
	String token;

	@Option(names = { "--header", "-H" },
			description = "Extra HTTP header 'Name: Value' (repeatable) — e.g. Cloudflare Access "
					+ "service-token headers when the server is behind a proxy/WAF.")
	List<String> headers = new ArrayList<>();

	@Option(names = "--project", description = "Project name (auto-detected from CI when omitted).")
	String project;

	@Option(names = "--commit", description = "Gate the latest run for this commit (preferred).")
	String commit;

	@Option(names = "--branch", description = "Gate the latest run on this branch (if --commit absent).")
	String branch;

	@Option(names = "--flag", description = "Restrict to a coverage flag / component.")
	String flag;

	private final UploadClient client;

	private final CiMetadataDetector detector;

	GateCommand(UploadClient client, CiMetadataDetector detector) {
		this.client = client;
		this.detector = detector;
	}

	@Override
	public Integer call() {
		CiMetadata ci = this.detector.detect();
		String resolvedProject = coalesce(this.project, ci.project());
		if (resolvedProject == null || resolvedProject.isBlank()) {
			System.err.println("error: --project is required (and could not be detected from the CI environment).");
			return ExitCodes.USAGE;
		}
		String resolvedCommit = coalesce(this.commit, ci.commit());
		String resolvedBranch = coalesce(this.branch, ci.branch());
		try {
			GateResponse gate = this.client.gate(this.url, this.token, UploadClient.parseHeaders(this.headers),
					resolvedProject, resolvedCommit, resolvedBranch, this.flag);
			if (!gate.found()) {
				System.err.println("error: no run found for the given project/commit/branch.");
				return ExitCodes.USAGE;
			}
			if (gate.passed()) {
				System.out.println("Quality gate PASSED (" + gate.status() + ").");
				return ExitCodes.OK;
			}
			System.out.println("Quality gate FAILED (" + gate.status() + ").");
			return ExitCodes.GATE_FAILED;
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
