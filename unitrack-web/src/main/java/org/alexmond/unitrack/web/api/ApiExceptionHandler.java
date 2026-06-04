package org.alexmond.unitrack.web.api;

import org.alexmond.unitrack.ingest.IngestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain/ingest exceptions to HTTP responses for the REST API. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IngestException.class)
    public ProblemDetail handleIngest(IngestException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid report upload");
        return problem;
    }
}
