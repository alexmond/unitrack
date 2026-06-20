package org.alexmond.unitrack.cli;

/**
 * Build metadata auto-detected from the CI environment; any field may be {@code null}.
 */
record CiMetadata(String ciProvider, String project, String branch, String commit, String buildUrl, String buildName,
		String repoUrl, String runKey, String prNumber) {

	static CiMetadata empty() {
		return new CiMetadata(null, null, null, null, null, null, null, null, null);
	}

}
