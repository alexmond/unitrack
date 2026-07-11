package org.alexmond.unitrack.ingest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoverageParsersTest {

	private final CoverageParsers parsers = new CoverageParsers(
			List.of(new JacocoXmlParser(), new CoberturaXmlParser(), new LcovParser(), new OpenCoverXmlParser()));

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

	private static final String OPENCOVER = """
			<?xml version="1.0" encoding="utf-8"?>
			<CoverageSession>
			  <Summary numSequencePoints="3" visitedSequencePoints="2" numBranchPoints="2" visitedBranchPoints="1"/>
			  <Modules>
			    <Module hash="AB-CD">
			      <ModuleName>MyApp</ModuleName>
			      <Files>
			        <File uid="1" fullPath="C:\\src\\MyApp\\Foo.cs"/>
			      </Files>
			      <Classes>
			        <Class>
			          <FullName>MyApp.Foo</FullName>
			          <Methods>
			            <Method visited="true">
			              <FileRef uid="1"/>
			              <SequencePoints>
			                <SequencePoint vc="3" sl="10" el="10"/>
			                <SequencePoint vc="1" sl="11" el="11"/>
			                <SequencePoint vc="0" sl="12" el="12"/>
			              </SequencePoints>
			              <BranchPoints>
			                <BranchPoint vc="1" sl="11"/>
			                <BranchPoint vc="0" sl="12"/>
			              </BranchPoints>
			            </Method>
			          </Methods>
			        </Class>
			      </Classes>
			    </Module>
			  </Modules>
			</CoverageSession>
			""";

	/**
	 * coverage.py emits Cobertura XML — detected by the {@code <coverage>} root, like JVM
	 * Cobertura.
	 */
	private static final String COVERAGE_PY = """
			<?xml version="1.0" ?>
			<!DOCTYPE coverage SYSTEM "http://cobertura.sourceforge.net/xml/coverage-04.dtd">
			<coverage version="7.4.0" line-rate="0.5" branch-rate="0">
			  <sources><source>/home/u/proj</source></sources>
			  <packages>
			    <package name="proj">
			      <classes>
			        <class name="foo.py" filename="proj/foo.py">
			          <lines>
			            <line number="1" hits="1"/>
			            <line number="2" hits="0"/>
			          </lines>
			        </class>
			      </classes>
			    </package>
			  </packages>
			</coverage>
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
		assertThat(r.files().get(0).fileName()).isEqualTo("foo.py");
		// getPath() rebuilds a clean package-relative path (no doubled package) for
		// links.
		assertThat(r.files().get(0).packageName() + "/" + r.files().get(0).fileName()).isEqualTo("app/foo.py");
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
	void detectsAndParsesOpenCover() {
		CoverageResults r = parse(OPENCOVER);
		// Sequence points grouped by line: sl 10 (vc3) + sl 11 (vc1) covered, sl 12 (vc0)
		// missed.
		assertThat(r.lineCovered()).isEqualTo(2);
		assertThat(r.lineMissed()).isEqualTo(1);
		// Branch points: vc1 covered, vc0 missed.
		assertThat(r.branchCovered()).isEqualTo(1);
		assertThat(r.branchMissed()).isEqualTo(1);
		assertThat(r.files()).hasSize(1);
		assertThat(r.files().get(0).fileName()).isEqualTo("Foo.cs");
		assertThat(r.files().get(0).packageName()).isEqualTo("C:/src/MyApp");
	}

	@Test
	void detectsAndParsesCoveragePyAsCobertura() {
		CoverageResults r = parse(COVERAGE_PY);
		assertThat(r.lineCovered()).isEqualTo(1);
		assertThat(r.lineMissed()).isEqualTo(1);
		assertThat(r.files()).hasSize(1);
		assertThat(r.files().get(0).fileName()).isEqualTo("foo.py");
		assertThat(r.files().get(0).packageName()).isEqualTo("proj");
	}

	@Test
	void rejectsUnknownFormat() {
		assertThatThrownBy(() -> parse("not a coverage report")).isInstanceOf(IngestException.class)
			.hasMessageContaining("Unrecognized coverage format");
	}

}
