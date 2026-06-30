package org.alexmond.unitrack.ingest;

/**
 * Metadata describing the origin of an uploaded set of reports. {@code baseBranch} and
 * {@code prNumber} describe a pull/merge-request build; both null for ordinary branch
 * builds. {@code module} is an explicit build module (#393) the uploader can attach so
 * results don't have to be grouped by the package-derivation heuristic; null when not
 * sent.
 */
public record IngestRequest(String project, String repoUrl, String branch, String flag, String commit, String buildUrl,
		String buildName, String ciProvider, String runKey, String baseBranch, Integer prNumber, String module) {

	/**
	 * Backward-compatible constructor for ordinary (non-PR) builds without a build name.
	 */
	public IngestRequest(String project, String repoUrl, String branch, String flag, String commit, String buildUrl,
			String ciProvider, String runKey) {
		this(project, repoUrl, branch, flag, commit, buildUrl, null, ciProvider, runKey, null, null, null);
	}

	/**
	 * Backward-compatible constructor predating the explicit {@code module} (#393).
	 */
	public IngestRequest(String project, String repoUrl, String branch, String flag, String commit, String buildUrl,
			String buildName, String ciProvider, String runKey, String baseBranch, Integer prNumber) {
		this(project, repoUrl, branch, flag, commit, buildUrl, buildName, ciProvider, runKey, baseBranch, prNumber,
				null);
	}
}
