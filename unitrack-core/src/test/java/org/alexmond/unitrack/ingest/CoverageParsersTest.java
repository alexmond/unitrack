package org.alexmond.unitrack.ingest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoverageParsersTest {

	private final CoverageParsers parsers = new CoverageParsers(
			List.of(new JacocoXmlParser(), new CoberturaXmlParser(), new LcovParser()));

	private static final String COBERTURA = """
			<?xml version="1.0"?>
			<!DOCTYPE coverage SYSTEM "http://cobertura.sourceforge.net/xml/coverage-04.dtd">
			<coverage line-rate="0.66" branch-rate="0.5" version="1.9">
			  <packages>
			    <package name="app">
			      <classes>
			        <class name="app.foo" filename="app/foo.py">
			          <methods>
			            <method name="foo" signature="()">
			              <lines>
			                <line number="1" hits="1" branch="false"/>
			              </lines>
			            </method>
			          </methods>
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

	private static final String LCOV = """
			TN:
			SF:src/app/foo.js
			FN:1,foo
			FNDA:3,foo
			DA:1,3
			DA:2,0
			DA:3,1
			BRDA:3,0,0,1
			BRDA:3,0,1,-
			end_of_record
			""";

	private CoverageResults parse(String content) {
		return this.parsers.parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void detectsAndParsesCobertura() {
		CoverageResults r = parse(COBERTURA);
		assertThat(r.lineCovered()).isEqualTo(2);
		assertThat(r.lineMissed()).isEqualTo(1);
		assertThat(r.branchCovered()).isEqualTo(1);
		assertThat(r.branchMissed()).isEqualTo(1);
		assertThat(r.files()).hasSize(1);
		assertThat(r.files().get(0).packageName()).isEqualTo("app");
		assertThat(r.files().get(0).fileName()).isEqualTo("app/foo.py");
	}

	@Test
	void detectsAndParsesLcov() {
		CoverageResults r = parse(LCOV);
		assertThat(r.lineCovered()).isEqualTo(2);
		assertThat(r.lineMissed()).isEqualTo(1);
		assertThat(r.branchCovered()).isEqualTo(1);
		assertThat(r.branchMissed()).isEqualTo(1);
		assertThat(r.methodCovered()).isEqualTo(1);
		assertThat(r.files()).hasSize(1);
		assertThat(r.files().get(0).packageName()).isEqualTo("src/app");
		assertThat(r.files().get(0).fileName()).isEqualTo("foo.js");
	}

	@Test
	void rejectsUnknownFormat() {
		assertThatThrownBy(() -> parse("not a coverage report")).isInstanceOf(IngestException.class)
			.hasMessageContaining("Unrecognized coverage format");
	}

}
