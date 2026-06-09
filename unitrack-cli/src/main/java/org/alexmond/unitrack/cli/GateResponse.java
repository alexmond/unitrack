package org.alexmond.unitrack.cli;

/** Result of a gate lookup: whether a run matched, and its verdict. */
record GateResponse(boolean found, boolean passed, String status) {
}
