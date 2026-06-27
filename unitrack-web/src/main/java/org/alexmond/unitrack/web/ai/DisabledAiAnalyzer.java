package org.alexmond.unitrack.web.ai;

import java.util.Optional;

import org.alexmond.unitrack.report.FailureCluster;

/** No-op analyzer used when AI is disabled (the default). */
class DisabledAiAnalyzer implements AiAnalyzer {

	@Override
	public boolean enabled() {
		return false;
	}

	@Override
	public Optional<FailureAnalysis> analyzeFailure(String projectName, FailureCluster cluster) {
		return Optional.empty();
	}

}
