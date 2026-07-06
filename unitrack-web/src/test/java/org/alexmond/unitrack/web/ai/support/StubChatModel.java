package org.alexmond.unitrack.web.ai.support;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * A deterministic {@link ChatModel} stub for tests — no network. Returns a fixed
 * assistant message (the JSON the analyzer's {@code .entity(...)} binds to) on every
 * call, and counts invocations so a test can assert a result was cached rather than
 * re-requested. Mirrors builder's stub, trimmed to the structured-output path (no
 * tool-calling loop).
 */
public class StubChatModel implements ChatModel {

	private final AtomicInteger calls = new AtomicInteger();

	private final String answer;

	public StubChatModel(String answer) {
		this.answer = answer;
	}

	/**
	 * Number of times the model was invoked — lets a test assert caching (no re-call).
	 */
	public int calls() {
		return this.calls.get();
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		this.calls.incrementAndGet();
		return new ChatResponse(List.of(new Generation(new AssistantMessage(this.answer))));
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return ToolCallingChatOptions.builder().build();
	}

}
