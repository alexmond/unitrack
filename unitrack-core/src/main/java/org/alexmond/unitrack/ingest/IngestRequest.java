package org.alexmond.unitrack.ingest;

import java.util.List;

/**
 * Metadata describing the origin of an uploaded set of reports. {@code baseBranch} and
 * {@code prNumber} describe a pull/merge-request build; both null for ordinary branch
 * builds. {@code module} is an explicit build module (#393) the uploader can attach so
 * results don't have to be grouped by the package-derivation heuristic; null when not
 * sent. {@code sourceManifest} is the checkout's repo-relative source file list
 * ({@code git ls-files}, #454) used to resolve coverage paths to working source links;
 * empty when the uploader sent none.
 */
public record IngestRequest(String project, String repoUrl, String branch, String flag, String commit, String buildUrl,
		String buildName, String ciProvider, String runKey, String baseBranch, Integer prNumber, String module,
		List<String> sourceManifest) {

	public IngestRequest {
		sourceManifest = (sourceManifest != null) ? List.copyOf(sourceManifest) : List.of();
	}

	/**
	 * Backward-compatible constructor for ordinary (non-PR) builds without a build name.
	 */
	public IngestRequest(String project, String repoUrl, String branch, String flag, String commit, String buildUrl,
			String ciProvider, String runKey) {
		this(project, repoUrl, branch, flag, commit, buildUrl, null, ciProvider, runKey, null, null, null, List.of());
	}

	/**
	 * Backward-compatible constructor predating the explicit {@code module} (#393).
	 */
	public IngestRequest(String project, String repoUrl, String branch, String flag, String commit, String buildUrl,
			String buildName, String ciProvider, String runKey, String baseBranch, Integer prNumber) {
		this(project, repoUrl, branch, flag, commit, buildUrl, buildName, ciProvider, runKey, baseBranch, prNumber,
				null, List.of());
	}

	/**
	 * Backward-compatible constructor predating the source manifest (#454).
	 */
	public IngestRequest(String project, String repoUrl, String branch, String flag, String commit, String buildUrl,
			String buildName, String ciProvider, String runKey, String baseBranch, Integer prNumber, String module) {
		this(project, repoUrl, branch, flag, commit, buildUrl, buildName, ciProvider, runKey, baseBranch, prNumber,
				module, List.of());
	}
}
