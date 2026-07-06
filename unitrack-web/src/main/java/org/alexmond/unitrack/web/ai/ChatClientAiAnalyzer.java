package org.alexmond.unitrack.web.ai;

import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.unitrack.report.FailureCluster;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * In-app failure analysis via Spring AI's {@link ChatClient}. **Off by default** —
 * bring-your-own-key: nothing calls an LLM unless {@code spring.ai.anthropic.api-key} is
 * set. One call per failure signature, cached so a recurring failure is analysed once.
 *
 * <p>
 * Structured output binds the model's JSON straight to {@link Diagnosis} via
 * {@code .entity(...)} — Spring AI generates the schema with its own
 * jsonschema-generator, the same one the MCP server pulls in, so there is no victools
 * version clash (the reason the earlier raw-SDK version parsed JSON by hand). Any API or
 * binding failure degrades to empty — AI never breaks the page.
 *
 * <p>
 * The Anthropic auto-config always publishes a {@link ChatModel} bean when the starter is
 * on the classpath (it tolerates a blank key), so {@link #enabled()} keys off the
 * api-key, not the bean's presence.
 */
@Service
@Slf4j
class ChatClientAiAnalyzer implements AiAnalyzer {

	private static final String SYSTEM_PROMPT = """
			You are a senior engineer triaging CI test failures. You are given a failure's exception
			type, message, normalized signature (type | message | top stack frame), and the affected
			tests. Identify the single most likely root cause and a concrete fix direction (where to
			look or what to change). Be specific and brief. If the evidence is thin, say so and lower
			the confidence. Do not invent stack frames or APIs you weren't given.""";

	private final ObjectProvider<ChatModel> chatModel;

	private final String apiKey;

	private final String modelName;

	ChatClientAiAnalyzer(ObjectProvider<ChatModel> chatModel, @Value("${spring.ai.anthropic.api-key:}") String apiKey,
			@Value("${spring.ai.anthropic.chat.options.model:claude}") String modelName) {
		this.chatModel = chatModel;
		this.apiKey = apiKey;
		this.modelName = modelName;
	}

	@Override
	public boolean enabled() {
		return this.apiKey != null && !this.apiKey.isBlank();
	}

	@Override
	// Spring unwraps the Optional, so #result is the FailureAnalysis (null when empty) —
	// only cache a successful analysis so a transient API failure retries next click.
	@Cacheable(value = "aiFailureAnalysis", key = "#cluster.signature()", unless = "#result == null")
	public Optional<FailureAnalysis> analyzeFailure(String projectName, FailureCluster cluster) {
		ChatModel model = this.chatModel.getIfAvailable();
		if (!enabled() || model == null) {
			return Optional.empty();
		}
		try {
			Diagnosis diagnosis = ChatClient.create(model)
				.prompt()
				.system(SYSTEM_PROMPT)
				.user(context(projectName, cluster))
				.call()
				.entity(Diagnosis.class);
			if (diagnosis == null || diagnosis.rootCause() == null || diagnosis.rootCause().isBlank()) {
				return Optional.empty();
			}
			return Optional.of(new FailureAnalysis(diagnosis.rootCause(), diagnosis.suggestion(),
					confidenceLabel(diagnosis.confidence()), this.modelName));
		}
		catch (RuntimeException ex) {
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
	 * Structured-output target Spring AI binds the model's JSON response to; confidence
	 * is a 0.0-1.0 self-estimate mapped to a label.
	 */
	record Diagnosis(String rootCause, String suggestion, double confidence) {
	}

}
