package org.alexmond.unitrack.ingest;

import org.alexmond.unitrack.domain.TestStatus;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class JUnitXmlParserTest {

	private final JUnitXmlParser parser = new JUnitXmlParser();

	@Test
	void parsesCountsStatusesAndFailures() throws Exception {
		try (InputStream in = resource("/samples/surefire-sample.xml")) {
			JUnitResults results = parser.parse(in);

			assertThat(results.totalTests()).isEqualTo(4);
			assertThat(results.failures()).isEqualTo(1);
			assertThat(results.errors()).isEqualTo(1);
			assertThat(results.skipped()).isEqualTo(1);
			assertThat(results.passed()).isEqualTo(1);

			ParsedSuite suite = results.suites().getFirst();
			assertThat(suite.name()).isEqualTo("com.example.CalculatorTest");
			assertThat(suite.cases()).hasSize(4);

			ParsedCase failing = suite.cases()
				.stream()
				.filter((c) -> c.name().equals("subtracts"))
				.findFirst()
				.orElseThrow();
			assertThat(failing.status()).isEqualTo(TestStatus.FAILED);
			assertThat(failing.failureType()).isEqualTo("org.opentest4j.AssertionFailedError");
			assertThat(failing.failureStacktrace()).contains("CalculatorTest.subtracts");

			ParsedCase erroring = suite.cases()
				.stream()
				.filter((c) -> c.name().equals("divides"))
				.findFirst()
				.orElseThrow();
			assertThat(erroring.status()).isEqualTo(TestStatus.ERROR);
		}
	}

	private InputStream resource(String path) {
		return getClass().getResourceAsStream(path);
	}

}
