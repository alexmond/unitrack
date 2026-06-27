package org.alexmond.unitrack.web.ai;

import java.util.Optional;
import java.util.stream.Collectors;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.unitrack.report.FailureCluster;
import org.springframework.cache.annotation.Cacheable;

/**
 * In-app failure analysis via the Anthropic Java SDK. One structured-output call per
 * failure signature, cached so a recurring failure is analysed once. Any API/parse
 * failure degrades to empty — AI never breaks the page.
 */
@Slf4j
class AnthropicAiAnalyzer implements AiAnalyzer {

	private static final String SYSTEM_PROMPT = """
			You are a senior engineer triaging CI test failures. You are given a failure's exception
			type, message, normalized signature (type | message | top stack frame), and the affected
			tests. Give the single most likely root cause and a concrete fix direction (where to look
			or what to change). Be specific and brief. If the evidence is thin, say so and lower the
			confidence. Do not invent stack frames or APIs you weren't given.""";

	private final AnthropicClient client;

	private final String model;

	AnthropicAiAnalyzer(AiProperties props) {
		this.model = props.getModel();
		this.client = AnthropicOkHttpClient.builder().apiKey(props.getApiKey()).timeout(props.getTimeout()).build();
	}

	@Override
	public boolean enabled() {
		return true;
	}

	@Override
	@Cacheable(value = "aiFailureAnalysis", key = "#cluster.signature()",
			unless = "#result == null || #result.isEmpty()")
	public Optional<FailureAnalysis> analyzeFailure(String projectName, FailureCluster cluster) {
		try {
			MessageCreateParams params = MessageCreateParams.builder()
				.model(this.model)
				.maxTokens(1024L)
				.system(SYSTEM_PROMPT)
				.outputConfig(Diagnosis.class)
				.addUserMessage(context(projectName, cluster))
				.build();
			StructuredMessage<Diagnosis> response = this.client.messages().create(params);
			Diagnosis d = response.content()
				.stream()
				.flatMap((block) -> block.text().stream())
				.findFirst()
				.orElseThrow()
				.text();
			return Optional
				.of(new FailureAnalysis(d.rootCause, d.suggestion, confidenceLabel(d.confidence), this.model));
		}
		catch (AnthropicException | RuntimeException ex) {
			log.warn("AI failure analysis unavailable for project '{}' ({})", projectName, ex.getMessage());
			return Optional.empty();
		}
	}

	private static String context(String projectName, FailureCluster cluster) {
		String type = (cluster.failureType() != null) ? cluster.failureType() : "(unknown)";
		String message = (cluster.sampleMessage() != null) ? ("Message: " + cluster.sampleMessage() + "\n") : "";
		String tests = cluster.tests().stream().limit(20).map((t) -> "  - " + t).collect(Collectors.joining("\n"));
		return "Project: " + projectName + "\n" + "Exception type: " + type + "\n" + message + "Signature: "
				+ cluster.signature() + "\n" + "Occurrences: " + cluster.occurrences() + " across "
				+ cluster.distinctTests() + " test(s)\n" + "Affected tests:\n" + tests + "\n";
	}

	private static String confidenceLabel(double confidence) {
		if (confidence >= 0.75) {
			return "high";
		}
		if (confidence >= 0.4) {
			return "medium";
		}
		return "low";
	}

	/**
	 * Structured-output target; static + public fields so the SDK's Jackson can bind it.
	 */
	static class Diagnosis {

		@JsonPropertyDescription("The single most likely root cause of the failure, one or two sentences.")
		public String rootCause;

		@JsonPropertyDescription("A concrete fix direction — where to look or what to change.")
		public String suggestion;

		@JsonPropertyDescription("Confidence from 0.0 to 1.0 that this diagnosis is correct.")
		public double confidence;

	}

}
