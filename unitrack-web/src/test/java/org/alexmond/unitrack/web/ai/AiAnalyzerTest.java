package org.alexmond.unitrack.web.ai;

import java.time.Instant;
import java.util.List;

import org.alexmond.unitrack.report.FailureCluster;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * AI gating: off by default (no api-key), and a no-op when disabled — no LLM is ever
 * called without a key configured.
 */
class AiAnalyzerTest {

	@SuppressWarnings("unchecked")
	private final ObjectProvider<ChatModel> models = mock(ObjectProvider.class);

	@Test
	void disabledWithoutApiKeyIsANoOp() {
		AiAnalyzer analyzer = new ChatClientAiAnalyzer(this.models, "", "claude-x");
		assertThat(analyzer.enabled()).as("off when no api-key").isFalse();
		assertThat(analyzer.analyzeFailure("proj", cluster())).isEmpty();
	}

	@Test
	void blankApiKeyIsTreatedAsDisabled() {
		AiAnalyzer analyzer = new ChatClientAiAnalyzer(this.models, "   ", "claude-x");
		assertThat(analyzer.enabled()).isFalse();
	}

	@Test
	void enabledWhenApiKeyPresentButStillSafeWithoutAModel() {
		given(this.models.getIfAvailable()).willReturn(null);
		AiAnalyzer analyzer = new ChatClientAiAnalyzer(this.models, "sk-ant-test", "claude-x");
		assertThat(analyzer.enabled()).as("on when keyed").isTrue();
		// No ChatModel resolvable -> degrades to empty rather than NPE-ing the page.
		assertThat(analyzer.analyzeFailure("proj", cluster())).isEmpty();
	}

	private static FailureCluster cluster() {
		return new FailureCluster("java.lang.AssertionError|boom|Foo.bar", "java.lang.AssertionError", "boom", 3, 2,
				List.of("com.x.A#a", "com.x.B#b"), Instant.now());
	}

}
