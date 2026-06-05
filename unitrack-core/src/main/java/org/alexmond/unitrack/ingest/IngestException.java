package org.alexmond.unitrack.ingest;

/**
 * Thrown when an uploaded report cannot be parsed or is missing required data. The web
 * layer maps this to HTTP 400 (see the web module's exception handler).
 */
public class IngestException extends RuntimeException {

	public IngestException(String message, Throwable cause) {
		super(message, cause);
	}

	public IngestException(String message) {
		super(message);
	}

}
