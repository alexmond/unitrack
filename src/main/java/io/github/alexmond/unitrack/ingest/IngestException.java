package io.github.alexmond.unitrack.ingest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when an uploaded report cannot be parsed. Surfaces as HTTP 400. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class IngestException extends RuntimeException {

    public IngestException(String message, Throwable cause) {
        super(message, cause);
    }

    public IngestException(String message) {
        super(message);
    }
}
