package org.alexmond.unitrack.cli;

/**
 * A transient failure (network error or a retryable 5xx) that the retry policy should
 * re-attempt.
 */
class RetryableUploadException extends RuntimeException {

	RetryableUploadException(String message, Throwable cause) {
		super(message, cause);
	}

}
