package io.github.alexmond.unitrack.ingest;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class JacocoXmlParserTest {

    private final JacocoXmlParser parser = new JacocoXmlParser();

    @Test
    void parsesAggregateAndPerFileCounters() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/samples/jacoco-sample.xml")) {
            CoverageResults results = parser.parse(in);

            assertThat(results.lineCovered()).isEqualTo(23);
            assertThat(results.lineMissed()).isEqualTo(7);
            assertThat(results.branchCovered()).isEqualTo(5);
            assertThat(results.methodCovered()).isEqualTo(5);
            // 23 / 30 = 76.67%
            assertThat(io.github.alexmond.unitrack.domain.CoverageReport.pct(23, 7))
                    .isCloseTo(76.67, within(0.1));

            assertThat(results.files()).hasSize(2);
            CoverageResults.ParsedFileCoverage calculator = results.files().stream()
                    .filter(f -> f.fileName().equals("Calculator.java")).findFirst().orElseThrow();
            assertThat(calculator.packageName()).isEqualTo("com/example");
            assertThat(calculator.lineCovered()).isEqualTo(18);
            assertThat(calculator.lineMissed()).isEqualTo(2);
        }
    }
}
