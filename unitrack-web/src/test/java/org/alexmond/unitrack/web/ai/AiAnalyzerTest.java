package org.alexmond.unitrack.web.ai;

import java.time.Instant;
import java.util.List;

import org.alexmond.unitrack.report.FailureCluster;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI gating: off by default, usable only when enabled + keyed; disabled analyzer is a
 * no-op.
 */
class AiAnalyzerTest {

	@Test
	void disabledAnalyzerIsANoOp() {
		AiAnalyzer analyzer = new DisabledAiAnalyzer();
		assertThat(analyzer.enabled()).isFalse();
		assertThat(analyzer.analyzeFailure("proj", cluster())).isEmpty();
	}

	@Test
	void propertiesUsableOnlyWhenEnabledAndKeyed() {
		AiProperties props = new AiProperties();
		assertThat(props.isUsable()).as("off by default").isFalse();
		props.setEnabled(true);
		assertThat(props.isUsable()).as("enabled but no key").isFalse();
		props.setApiKey("sk-ant-test");
		assertThat(props.isUsable()).as("enabled + key").isTrue();
	}

	private static FailureCluster cluster() {
		return new FailureCluster("java.lang.AssertionError|boom|Foo.bar", "java.lang.AssertionError", "boom", 3, 2,
				List.of("com.x.A#a", "com.x.B#b"), Instant.now());
	}

}
