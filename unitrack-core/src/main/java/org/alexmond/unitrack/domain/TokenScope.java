package org.alexmond.unitrack.domain;

/**
 * What an {@link ApiToken} is allowed to do. {@code FULL} acts as its owning user;
 * {@code INGEST} is a least-privilege CI secret that may only call the ingest endpoint —
 * if leaked it can't read private data or manage anything. {@code ACTION} is a
 * least-privilege MCP credential: it authenticates as its owner but only on the MCP
 * transport, so it can drive the AI write tools (within the owner's project permissions)
 * yet can't browse the UI/REST API.
 */
public enum TokenScope {

	FULL, INGEST, ACTION

}
