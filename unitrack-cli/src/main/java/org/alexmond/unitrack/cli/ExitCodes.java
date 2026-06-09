package org.alexmond.unitrack.cli;

/** Stable exit-code contract shared by the CLI commands. */
final class ExitCodes {

	/** Success. */
	static final int OK = 0;

	/** The quality gate did not pass. */
	static final int GATE_FAILED = 1;

	/** Usage or configuration error (bad flag, no files matched). */
	static final int USAGE = 2;

	/** Transport failure (network error, or a server error after retries). */
	static final int TRANSPORT = 3;

	/** The server rejected the payload (e.g. 413 too large, 422 unprocessable). */
	static final int REJECTED = 4;

	private ExitCodes() {
	}

}
