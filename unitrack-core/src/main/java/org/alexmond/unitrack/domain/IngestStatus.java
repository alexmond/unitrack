package org.alexmond.unitrack.domain;

/**
 * Lifecycle of an ingest upload. {@code QUEUED} is reserved for future async processing.
 */
public enum IngestStatus {

	QUEUED, PROCESSING, PROCESSED, FAILED

}
