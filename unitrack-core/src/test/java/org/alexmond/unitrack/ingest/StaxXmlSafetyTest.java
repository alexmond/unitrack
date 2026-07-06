package org.alexmond.unitrack.ingest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The StAX report parsers must tolerate a DOCTYPE (Cobertura ships one) yet never resolve
 * external entities — i.e. be XXE-safe (#369). Exercised through {@link JUnitXmlParser},
 * which shares the {@link StaxXml} hardened factory with every XML parser.
 */
class StaxXmlSafetyTest {

	private final JUnitXmlParser parser = new JUnitXmlParser();

	private static InputStream xml(String content) {
		return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void parsesDespiteADoctypeDeclaration() {
		String doc = "<?xml version=\"1.0\"?><!DOCTYPE testsuite>"
				+ "<testsuite name=\"S\" tests=\"1\"><testcase name=\"t\" classname=\"C\" time=\"0.01\"/></testsuite>";
		JUnitResults results = this.parser.parse(xml(doc));
		assertThat(results.suites()).hasSize(1);
		assertThat(results.suites().getFirst().cases()).hasSize(1);
	}

	@Test
	void doesNotResolveAnExternalEntity() {
		String doc = "<?xml version=\"1.0\"?>"
				+ "<!DOCTYPE testsuite [ <!ENTITY xxe SYSTEM \"file:///etc/hostname\"> ]>"
				+ "<testsuite name=\"&xxe;\" tests=\"0\"></testsuite>";
		// The host file must never be read. External entities are neutralized to an empty
		// stream, so the parse either fails fast or yields a blank (un-expanded) name —
		// both safe; what matters is the file contents never leak in.
		JUnitResults results;
		try {
			results = this.parser.parse(xml(doc));
		}
		catch (IngestException refused) {
			return;
		}
		String name = results.suites().isEmpty() ? "" : results.suites().getFirst().name();
		assertThat(name).isBlank();
	}

}
