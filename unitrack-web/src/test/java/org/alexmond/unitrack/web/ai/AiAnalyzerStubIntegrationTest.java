package org.alexmond.unitrack.web.ai;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.alexmond.unitrack.report.FailureCluster;
import org.alexmond.unitrack.web.ai.support.StubChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Spring AI path end-to-end with a stubbed {@link ChatModel} (no key, no
 * network): the model's JSON is bound to the analyzer's structured-output record and
 * mapped to a {@link FailureAnalysis}, and a repeat lookup for the same signature is
 * served from cache (the model is not re-invoked — a cost regression guard). Builder's
 * stub pattern, applied here.
 */
@SpringBootTest(properties = "spring.ai.anthropic.api-key=test-stub-key")
@Import(AiAnalyzerStubIntegrationTest.StubConfig.class)
class AiAnalyzerStubIntegrationTest {

	@Autowired
	private AiAnalyzer analyzer;

	@Autowired
	private ChatModel chatModel;

	@Test
	void bindsStructuredDiagnosisAndCachesPerSignature() {
		assertThat(this.analyzer.enabled()).as("keyed -> enabled").isTrue();

		Optional<FailureAnalysis> first = this.analyzer.analyzeFailure("checkout-service", cluster());
		assertThat(first).isPresent();
		assertThat(first.get().rootCause()).contains("cart");
		assertThat(first.get().suggestion()).isNotBlank();
		assertThat(first.get().confidence()).isEqualTo("high"); // 0.82 -> high
		assertThat(first.get().model()).isNotBlank();

		int afterFirst = ((StubChatModel) this.chatModel).calls();
		assertThat(afterFirst).isPositive();
		// Same signature -> cache hit -> the (paid) model is not called again.
		this.analyzer.analyzeFailure("checkout-service", cluster());
		assertThat(((StubChatModel) this.chatModel).calls()).isEqualTo(afterFirst);
	}

	private static FailureCluster cluster() {
		return new FailureCluster(
				"java.lang.NullPointerException|cart is null|CheckoutService.checkout(CheckoutService.java:42)",
				"java.lang.NullPointerException", "Cannot invoke Cart.total() because cart is null", 5, 3,
				List.of("com.shop.CheckoutServiceTest#empty", "com.shop.OrderFlowTest#endToEnd"), Instant.now());
	}

	@TestConfiguration
	static class StubConfig {

		@Bean
		@Primary
		ChatModel stubChatModel() {
			return new StubChatModel(
					"""
							{"rootCause":"The CheckoutService.cart field is never initialized in test setup, so checkout() NPEs.",
							 "suggestion":"Construct or inject a Cart in @BeforeEach before calling checkout().",
							 "confidence":0.82}
							""");
		}

	}

}
