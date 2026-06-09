package org.alexmond.unitrack.cli;

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

	@Option(names = "--project", required = true, description = "Project name.")
	String project;

	@Option(names = "--commit", description = "Gate the latest run for this commit (preferred).")
	String commit;

	@Option(names = "--branch", description = "Gate the latest run on this branch (if --commit absent).")
	String branch;

	@Option(names = "--flag", description = "Restrict to a coverage flag / component.")
	String flag;

	private final UploadClient client;

	GateCommand(UploadClient client) {
		this.client = client;
	}

	@Override
	public Integer call() {
		try {
			GateResponse gate = this.client.gate(this.url, this.token, this.project, this.commit, this.branch,
					this.flag);
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

}
