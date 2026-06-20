package org.alexmond.unitrack.ingest;

/**
 * Metadata describing the origin of an uploaded set of reports. {@code baseBranch} and
 * {@code prNumber} describe a pull/merge-request build; both null for ordinary branch
 * builds.
 */
public record IngestRequest(String project, String repoUrl, String branch, String flag, String commit, String buildUrl,
		String buildName, String ciProvider, String runKey, String baseBranch, Integer prNumber) {

	/**
	 * Backward-compatible constructor for ordinary (non-PR) builds without a build name.
	 */
	public IngestRequest(String project, String repoUrl, String branch, String flag, String commit, String buildUrl,
			String ciProvider, String runKey) {
		this(project, repoUrl, branch, flag, commit, buildUrl, null, ciProvider, runKey, null, null);
	}
}
