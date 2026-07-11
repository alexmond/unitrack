package org.alexmond.unitrack.ingest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import edu.hm.hafner.coverage.CoverageParser.ProcessingMode;
import edu.hm.hafner.coverage.parser.CoberturaParser;
import edu.hm.hafner.coverage.parser.JacocoParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fidelity guard for #339: coverage-model must reproduce the hand-rolled parsers' LINE
 * and BRANCH numbers (which drive coverage% + the quality gate) for the formats it now
 * backs — JaCoCo and Cobertura. LCOV and OpenCover stay on the hand-rolled parsers
 * (coverage-model under-counts LCOV branch and is stricter on OpenCover), so they aren't
 * checked here.
 */
class CoverageModelFidelityTest {

	@Test
	void jacocoLineAndBranchMatch() throws Exception {
		CoverageResults hand;
		try (InputStream in = getClass().getResourceAsStream("/samples/jacoco-sample.xml")) {
			hand = new JacocoXmlParser().parse(in);
		}
		CoverageResults model;
		try (InputStream in = getClass().getResourceAsStream("/samples/jacoco-sample.xml")) {
			model = CoverageModelAdapter.parse(new JacocoParser(ProcessingMode.IGNORE_ERRORS), in, "jacoco");
		}
		assertLineBranchMatch(hand, model);
	}

	@Test
	void jacocoFilePathsAreCleanRepoRelative() throws Exception {
		CoverageResults model;
		try (InputStream in = getClass().getResourceAsStream("/samples/jacoco-sample.xml")) {
			model = CoverageModelAdapter.parse(new JacocoParser(ProcessingMode.IGNORE_ERRORS), in, "jacoco");
		}
		// Guards the #454 regression: file paths must be clean package-relative
		// (org/ex/Foo.java), so GitHub source links + PR annotations resolve — not a
		// dotted
		// package doubled onto the full relative path (org.ex/org/ex/Foo.java).
		assertThat(model.files()).isNotEmpty().allSatisfy((f) -> {
			assertThat(f.fileName()).as("bare file name").doesNotContain("/");
			assertThat(f.packageName()).as("slashed package, not dotted").doesNotContain(".");
			assertThat(f.packageName() + "/" + f.fileName()).as("no doubled package")
				.doesNotContain(f.packageName() + "/" + f.packageName());
		});
	}

	@Test
	void coberturaLineAndBranchMatch() {
		CoverageResults hand = new CoberturaXmlParser().parse(bytes(COBERTURA));
		CoverageResults model = CoverageModelAdapter.parse(new CoberturaParser(ProcessingMode.IGNORE_ERRORS),
				bytes(COBERTURA), "cobertura");
		assertLineBranchMatch(hand, model);
	}

	private static void assertLineBranchMatch(CoverageResults hand, CoverageResults model) {
		assertThat(model.lineCovered()).as("lineCovered").isEqualTo(hand.lineCovered());
		assertThat(model.lineMissed()).as("lineMissed").isEqualTo(hand.lineMissed());
		assertThat(model.branchCovered()).as("branchCovered").isEqualTo(hand.branchCovered());
		assertThat(model.branchMissed()).as("branchMissed").isEqualTo(hand.branchMissed());
	}

	private static InputStream bytes(String s) {
		return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
	}

	private static final String COBERTURA = """
			<?xml version="1.0"?>
			<!DOCTYPE coverage SYSTEM "http://cobertura.sourceforge.net/xml/coverage-04.dtd">
			<coverage line-rate="0.66" branch-rate="0.5" version="1.9">
			  <packages>
			    <package name="app">
			      <classes>
			        <class name="app.foo" filename="app/foo.py">
			          <methods/>
			          <lines>
			            <line number="1" hits="1" branch="false"/>
			            <line number="2" hits="0" branch="false"/>
			            <line number="3" hits="5" branch="true" condition-coverage="50% (1/2)"/>
			          </lines>
			        </class>
			      </classes>
			    </package>
			  </packages>
			</coverage>
			""";

}
