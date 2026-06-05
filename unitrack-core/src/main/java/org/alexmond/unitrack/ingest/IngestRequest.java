package org.alexmond.unitrack.ingest;

/** Metadata describing the origin of an uploaded set of reports. */
public record IngestRequest(String project, String repoUrl, String branch, String flag, String commit, String buildUrl,
		String ciProvider) {
}
