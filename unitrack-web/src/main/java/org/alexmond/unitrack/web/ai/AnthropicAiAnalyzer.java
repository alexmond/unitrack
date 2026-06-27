package org.alexmond.unitrack.web.ai;

import java.util.Optional;
import java.util.stream.Collectors;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.unitrack.report.FailureCluster;
import org.springframework.cache.annotation.Cacheable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * In-app failure analysis via the Anthropic Java SDK. One call per failure signature,
 * cached so a recurring failure is analysed once. The model is asked for a small JSON
 * object which we parse with the app's Jackson — we deliberately avoid the SDK's
 * structured-output helper because it builds a schema via victools jsonschema-generator,
 * whose version clashes with the one Spring AI (the MCP server) pulls in. Any API/parse
 * failure degrades to empty — AI never breaks the page.
 */
@Slf4j
class AnthropicAiAnalyzer implements AiAnalyzer {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final String SYSTEM_PROMPT = """
			You are a senior engineer triaging CI test failures. You are given a failure's exception
			type, message, normalized signature (type | message | top stack frame), and the affected
			tests. Identify the single most likely root cause and a concrete fix direction (where to
			look or what to change). Be specific and brief. If the evidence is thin, say so and lower
			the confidence. Do not invent stack frames or APIs you weren't given.

			Respond with ONLY a JSON object, no prose and no markdown fences:
			{"rootCause": "<1-2 sentences>", "suggestion": "<concrete fix direction>", "confidence": <0.0-1.0>}""";

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
	// Spring unwraps the Optional, so #result is the FailureAnalysis (null when empty) —
	// only
	// cache a successful analysis so a transient API failure retries next click.
	@Cacheable(value = "aiFailureAnalysis", key = "#cluster.signature()", unless = "#result == null")
	public Optional<FailureAnalysis> analyzeFailure(String projectName, FailureCluster cluster) {
		try {
			MessageCreateParams params = MessageCreateParams.builder()
				.model(this.model)
				.maxTokens(1024L)
				.system(SYSTEM_PROMPT)
				.addUserMessage(context(projectName, cluster))
				.build();
			String text = this.client.messages()
				.create(params)
				.content()
				.stream()
				.flatMap((block) -> block.text().stream())
				.map(TextBlock::text)
				.collect(Collectors.joining());
			JsonNode json = MAPPER.readTree(stripFences(text));
			String rootCause = json.path("rootCause").asString("");
			if (rootCause.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(new FailureAnalysis(rootCause, json.path("suggestion").asString(""),
					confidenceLabel(json.path("confidence").asDouble(0.5)), this.model));
		}
		catch (RuntimeException ex) {
			log.warn("AI failure analysis unavailable for project '{}' ({})", projectName, ex.getMessage());
			return Optional.empty();
		}
	}

	/**
	 * Tolerate a model that wraps the JSON in ```json … ``` fences despite instructions.
	 */
	private static String stripFences(String text) {
		String t = text.strip();
		if (t.startsWith("```")) {
			int firstNewline = t.indexOf('\n');
			int lastFence = t.lastIndexOf("```");
			if (firstNewline > 0 && lastFence > firstNewline) {
				return t.substring(firstNewline + 1, lastFence).strip();
			}
		}
		return t;
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

}
