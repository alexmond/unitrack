package org.alexmond.unitrack.cli;

/** A failure that maps to a specific CLI {@link ExitCodes exit code}. */
class UploadException extends RuntimeException {

	private final int exitCode;

	UploadException(int exitCode, String message) {
		this(exitCode, message, null);
	}

	UploadException(int exitCode, String message, Throwable cause) {
		super(message, cause);
		this.exitCode = exitCode;
	}

	int exitCode() {
		return this.exitCode;
	}

}
