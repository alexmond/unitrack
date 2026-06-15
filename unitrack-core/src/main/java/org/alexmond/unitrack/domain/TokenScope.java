package org.alexmond.unitrack.domain;

/**
 * What an {@link ApiToken} is allowed to do. {@code FULL} acts as its owning user;
 * {@code INGEST} is a least-privilege CI secret that may only call the ingest endpoint —
 * if leaked it can't read private data or manage anything.
 */
public enum TokenScope {

	FULL, INGEST

}
