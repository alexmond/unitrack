package org.alexmond.unitrack.ingest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.alexmond.unitrack.domain.TestStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** xUnit.net + NUnit (test results) and Gatling (perf) parsers + detection routing. */
class XUnitNUnitGatlingParserTest {

	private static ByteArrayInputStream in(String s) {
		return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
	}

	private static final String XUNIT = """
			<?xml version="1.0" encoding="utf-8"?>
			<assemblies>
			  <assembly name="Shop.Tests.dll" test-framework="xUnit.net 2.4.2">
			    <collection name="col">
			      <test name="Shop.CartTests.Adds" type="Shop.CartTests" method="Adds" time="0.012" result="Pass"/>
			      <test name="Shop.CartTests.Subtracts" type="Shop.CartTests" method="Subtracts" time="0.008" result="Fail">
			        <failure exception-type="Xunit.Sdk.EqualException"><message>Assert.Equal() Failure</message><stack-trace>at Shop.CartTests.Subtracts()</stack-trace></failure>
			      </test>
			      <test name="Shop.CartTests.Pends" type="Shop.CartTests" method="Pends" time="0" result="Skip"><reason>wip</reason></test>
			    </collection>
			  </assembly>
			</assemblies>""";

	private static final String NUNIT = """
			<?xml version="1.0" encoding="utf-8"?>
			<test-run id="1" testcasecount="3" result="Failed">
			  <test-suite type="Assembly" name="Shop.Tests">
			    <test-suite type="TestFixture" name="CalcTests" classname="Shop.CalcTests">
			      <test-case name="Adds" classname="Shop.CalcTests" methodname="Adds" result="Passed" duration="0.012"/>
			      <test-case name="Subtracts" classname="Shop.CalcTests" methodname="Subtracts" result="Failed" duration="0.008">
			        <failure><message>expected 2 but was 3</message><stack-trace>at Shop.CalcTests.Subtracts()</stack-trace></failure>
			      </test-case>
			      <test-case name="Ignored" classname="Shop.CalcTests" methodname="Ignored" result="Skipped" duration="0"/>
			    </test-suite>
			  </test-suite>
			</test-run>""";

	// Gatling 3.x simulation.log: tab-separated;
	// REQUEST<TAB>group<TAB>name<TAB>start<TAB>end<TAB>status[<TAB>msg]
	private static final String GATLING = "RUN\tShopSim\tshopsim\t1700000000000\t\t3.9.5\n"
			+ "REQUEST\t\tGET /cart\t1700000000000\t1700000000120\tOK\t\n"
			+ "REQUEST\t\tGET /cart\t1700000000200\t1700000000260\tOK\t\n"
			+ "REQUEST\t\tPOST /checkout\t1700000000300\t1700000000800\tKO\tstatus 500\n";

	@Test
	void xunitParsesAndDetects() {
		XUnitXmlParser parser = new XUnitXmlParser();
		assertThat(parser.supports(XUNIT)).isTrue();
		JUnitResults r = parser.parse(in(XUNIT));
		assertThat(r.suites()).singleElement().satisfies((s) -> assertThat(s.name()).isEqualTo("Shop.CartTests"));
		assertThat(r.totalTests()).isEqualTo(3);
		assertThat(r.failures()).isEqualTo(1);
		assertThat(r.skipped()).isEqualTo(1);
		ParsedCase failed = byName(r, "Subtracts");
		assertThat(failed.status()).isEqualTo(TestStatus.FAILED);
		assertThat(failed.failureMessage()).contains("Assert.Equal");
		assertThat(failed.durationMs()).isEqualTo(8);
	}

	@Test
	void nunitParsesAndDetects() {
		NUnitXmlParser parser = new NUnitXmlParser();
		assertThat(parser.supports(NUNIT)).isTrue();
		JUnitResults r = parser.parse(in(NUNIT));
		assertThat(r.suites()).singleElement().satisfies((s) -> assertThat(s.name()).isEqualTo("Shop.CalcTests"));
		assertThat(r.totalTests()).isEqualTo(3);
		assertThat(r.failures()).isEqualTo(1);
		assertThat(r.skipped()).isEqualTo(1);
		ParsedCase failed = byName(r, "Subtracts");
		assertThat(failed.failureMessage()).isEqualTo("expected 2 but was 3");
		assertThat(failed.durationMs()).isEqualTo(8);
	}

	@Test
	void gatlingParsesAndDetects() {
		GatlingLogParser parser = new GatlingLogParser();
		assertThat(parser.supports(GATLING)).isTrue();
		PerfResults r = parser.parse(in(GATLING));
		assertThat(r.sampleCount()).isEqualTo(3);
		assertThat(r.errorCount()).isEqualTo(1); // the KO
		assertThat(r.labels()).extracting(PerfResults.LabelStats::label).contains("GET /cart", "POST /checkout");
		PerfResults.LabelStats checkout = r.labels()
			.stream()
			.filter((l) -> l.label().equals("POST /checkout"))
			.findFirst()
			.orElseThrow();
		assertThat(checkout.meanMs()).isEqualTo(500.0); // 800 - 300
		assertThat(checkout.errorCount()).isEqualTo(1);
	}

	@Test
	void dispatcherRoutesXunitAndNunit() {
		TestResultParsers dispatcher = new TestResultParsers(
				List.of(new JUnitXmlParser(), new XUnitXmlParser(), new NUnitXmlParser()));
		assertThat(dispatcher.parse(in(XUNIT)).format()).isEqualTo("xunit");
		assertThat(dispatcher.parse(in(NUNIT)).format()).isEqualTo("nunit");
	}

	private static ParsedCase byName(JUnitResults r, String name) {
		return r.suites().getFirst().cases().stream().filter((c) -> c.name().equals(name)).findFirst().orElseThrow();
	}

}
