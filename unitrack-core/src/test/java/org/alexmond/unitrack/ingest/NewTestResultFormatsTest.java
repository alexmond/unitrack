package org.alexmond.unitrack.ingest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.alexmond.unitrack.domain.TestStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** CTRF, TRX and Go {@code test -json} test-result parsers + auto-detection routing. */
class NewTestResultFormatsTest {

	private static ByteArrayInputStream in(String s) {
		return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
	}

	private static final String CTRF = """
			{"results":{"tool":{"name":"jest"},"summary":{"tests":3,"passed":1,"failed":1,"skipped":1},
			"tests":[
			  {"name":"adds","status":"passed","duration":12,"suite":"math"},
			  {"name":"subtracts","status":"failed","duration":8,"message":"expected 2","trace":"at math.test.js:5","suite":"math"},
			  {"name":"divides","status":"skipped","duration":0,"suite":"math"}
			]}}""";

	private static final String TRX = """
			<?xml version="1.0" encoding="UTF-8"?>
			<TestRun xmlns="http://microsoft.com/schemas/VisualStudio/TeamTest/2010">
			  <Results>
			    <UnitTestResult testId="id1" testName="Adds" outcome="Passed" duration="00:00:00.0120000"/>
			    <UnitTestResult testId="id2" testName="Subtracts" outcome="Failed" duration="00:00:00.0080000">
			      <Output><ErrorInfo><Message>expected 2</Message><StackTrace>at Calc.cs:5</StackTrace></ErrorInfo></Output>
			    </UnitTestResult>
			    <UnitTestResult testId="id3" testName="Skips" outcome="NotExecuted" duration="00:00:00"/>
			  </Results>
			  <TestDefinitions>
			    <UnitTest id="id1" name="Adds"><TestMethod className="Shop.CalcTests, Shop.Tests" name="Adds"/></UnitTest>
			    <UnitTest id="id2" name="Subtracts"><TestMethod className="Shop.CalcTests" name="Subtracts"/></UnitTest>
			    <UnitTest id="id3" name="Skips"><TestMethod className="Shop.CalcTests" name="Skips"/></UnitTest>
			  </TestDefinitions>
			</TestRun>""";

	private static final String GO_TEST = """
			{"Time":"2026-01-01T00:00:00Z","Action":"run","Package":"shop/cart","Test":"TestAdd"}
			{"Time":"2026-01-01T00:00:00Z","Action":"output","Package":"shop/cart","Test":"TestAdd","Output":"=== RUN TestAdd\\n"}
			{"Time":"2026-01-01T00:00:00Z","Action":"pass","Package":"shop/cart","Test":"TestAdd","Elapsed":0.012}
			{"Time":"2026-01-01T00:00:00Z","Action":"run","Package":"shop/cart","Test":"TestSub"}
			{"Time":"2026-01-01T00:00:00Z","Action":"output","Package":"shop/cart","Test":"TestSub","Output":"sub failed\\n"}
			{"Time":"2026-01-01T00:00:00Z","Action":"fail","Package":"shop/cart","Test":"TestSub","Elapsed":0.004}
			{"Time":"2026-01-01T00:00:00Z","Action":"pass","Package":"shop/cart","Elapsed":0.02}
			""";

	@Test
	void ctrfParsesAndDetects() {
		CtrfJsonParser parser = new CtrfJsonParser();
		assertThat(parser.supports(CTRF)).isTrue();
		JUnitResults r = parser.parse(in(CTRF));
		assertThat(r.suites()).singleElement().satisfies((s) -> assertThat(s.name()).isEqualTo("math"));
		assertThat(r.totalTests()).isEqualTo(3);
		assertThat(r.failures()).isEqualTo(1);
		assertThat(r.skipped()).isEqualTo(1);
		assertThat(r.passed()).isEqualTo(1);
		ParsedCase failed = r.suites()
			.getFirst()
			.cases()
			.stream()
			.filter((c) -> c.status() == TestStatus.FAILED)
			.findFirst()
			.orElseThrow();
		assertThat(failed.failureMessage()).isEqualTo("expected 2");
		assertThat(failed.durationMs()).isEqualTo(8);
	}

	@Test
	void trxParsesAndDetects() {
		TrxXmlParser parser = new TrxXmlParser();
		assertThat(parser.supports(TRX)).isTrue();
		JUnitResults r = parser.parse(in(TRX));
		assertThat(r.suites()).singleElement().satisfies((s) -> assertThat(s.name()).isEqualTo("Shop.CalcTests"));
		assertThat(r.totalTests()).isEqualTo(3);
		assertThat(r.failures()).isEqualTo(1);
		assertThat(r.skipped()).isEqualTo(1);
		ParsedCase failed = r.suites()
			.getFirst()
			.cases()
			.stream()
			.filter((c) -> c.status() == TestStatus.FAILED)
			.findFirst()
			.orElseThrow();
		assertThat(failed.name()).isEqualTo("Subtracts");
		assertThat(failed.failureMessage()).isEqualTo("expected 2");
		assertThat(failed.durationMs()).isEqualTo(8);
	}

	@Test
	void goTestParsesAndDetects() {
		GoTestJsonParser parser = new GoTestJsonParser();
		assertThat(parser.supports(GO_TEST)).isTrue();
		JUnitResults r = parser.parse(in(GO_TEST));
		assertThat(r.suites()).singleElement().satisfies((s) -> assertThat(s.name()).isEqualTo("shop/cart"));
		assertThat(r.totalTests()).isEqualTo(2);
		assertThat(r.failures()).isEqualTo(1);
		ParsedCase add = r.suites()
			.getFirst()
			.cases()
			.stream()
			.filter((c) -> c.name().equals("TestAdd"))
			.findFirst()
			.orElseThrow();
		assertThat(add.status()).isEqualTo(TestStatus.PASSED);
		assertThat(add.durationMs()).isEqualTo(12);
		ParsedCase sub = r.suites()
			.getFirst()
			.cases()
			.stream()
			.filter((c) -> c.name().equals("TestSub"))
			.findFirst()
			.orElseThrow();
		assertThat(sub.status()).isEqualTo(TestStatus.FAILED);
		assertThat(sub.failureStacktrace()).contains("sub failed");
	}

	@Test
	void dispatcherRoutesEachFormatByContent() {
		TestResultParsers dispatcher = new TestResultParsers(
				List.of(new JUnitXmlParser(), new CtrfJsonParser(), new TrxXmlParser(), new GoTestJsonParser()));
		assertThat(dispatcher.parse(in(CTRF)).format()).isEqualTo("ctrf");
		assertThat(dispatcher.parse(in(TRX)).format()).isEqualTo("trx");
		assertThat(dispatcher.parse(in(GO_TEST)).format()).isEqualTo("go-test");
		String junit = "<testsuite name=\"S\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\">"
				+ "<testcase name=\"t\" classname=\"S\"/></testsuite>";
		assertThat(dispatcher.parse(in(junit)).format()).isEqualTo("junit");
	}

}
