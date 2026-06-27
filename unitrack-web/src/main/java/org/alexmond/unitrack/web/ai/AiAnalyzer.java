package org.alexmond.unitrack.web.ai;

import java.util.Optional;

import org.alexmond.unitrack.report.FailureCluster;

/**
 * Provider-agnostic AI analysis of test results. The in-app Anthropic implementation
 * ships first; an MCP-delegated implementation can be added behind the same interface
 * later.
 */
public interface AiAnalyzer {

	/** Whether analysis is available (feature enabled + a key configured). */
	boolean enabled();

	/**
	 * Root-cause + fix direction for a failure cluster, or empty when AI is disabled or
	 * the call fails. Implementations cache the result per cluster signature.
	 */
	Optional<FailureAnalysis> analyzeFailure(String projectName, FailureCluster cluster);

}
