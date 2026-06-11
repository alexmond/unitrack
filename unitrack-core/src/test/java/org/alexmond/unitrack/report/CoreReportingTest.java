package org.alexmond.unitrack.report;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

import org.alexmond.unitrack.domain.FlakyStatus;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.ingest.IngestRequest;
import org.alexmond.unitrack.ingest.IngestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-context test for the core report services: a Spring Boot context with the actual
 * repositories and an embedded H2, exercising the genuine ingest -> report flow with no
 * mocks. Demonstrates that core is fully testable on its own.
 */
@SpringBootTest
@Transactional
class CoreReportingTest {

	@Autowired
	private IngestService ingest;

	@Autowired
	private ReportingService reporting;

	@Autowired
	private QualityGateService gate;

	@Autowired
	private TestRegressionService regression;

	@Autowired
	private PerfRegressionService perfRegression;

	@Autowired
	private BlameService blame;

	@Autowired
	private PerformanceService performance;

	@Autowired
	private FlakyTestService flaky;

	@Autowired
	private FailureClusteringService clustering;

	@Autowired
	private TriageService triage;

	@Autowired
	private ProjectHealthService projectHealth;

	@Autowired
	private ProjectSettingsService settings;

	@Autowired
	private org.alexmond.unitrack.ingest.PerfIngestService perfIngest;

	@Autowired
	private org.alexmond.unitrack.repository.PerfTransactionRepository perfTransactions;

	@Autowired
	private PerfRunRegressionService perfRunRegression;

	@Autowired
	private PerfRunDetailService perfRunDetail;

	private static String tc(String name, double time, boolean fail) {
		String body = "<testcase name=\"" + name + "\" classname=\"com.x.G\" time=\"" + time + "\"";
		return fail ? body + "><failure message=\"boom\" type=\"java.lang.AssertionError\">trace</failure></testcase>"
				: body + "/>";
	}

	private static byte[] junit(boolean aFail, double aTime, double bTime) {
		int failures = aFail ? 1 : 0;
		return ("<?xml version=\"1.0\"?><testsuite name=\"com.x.G\" tests=\"2\" failures=\"" + failures
				+ "\" errors=\"0\" skipped=\"0\" time=\"" + (aTime + bTime) + "\">" + tc("a", aTime, aFail)
				+ tc("b", bTime, false) + "</testsuite>")
			.getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] jacoco(int covered, int missed) {
		return ("<?xml version=\"1.0\"?><report name=\"r\"><counter type=\"LINE\" missed=\"" + missed + "\" covered=\""
				+ covered + "\"/><package name=\"p\"><sourcefile name=\"F.java\"><counter type=\"LINE\" missed=\""
				+ missed + "\" covered=\"" + covered + "\"/></sourcefile></package></report>")
			.getBytes(StandardCharsets.UTF_8);
	}

	private static List<Supplier<InputStream>> stream(byte[] data) {
		return List.of(() -> new ByteArrayInputStream(data));
	}

	private TestRun ingest(String commit, byte[] junit, byte[] jacoco) {
		IngestRequest meta = new IngestRequest("core-demo", "https://github.com/acme/core-demo", "main", "default",
				commit, null, null, null);
		return this.ingest.ingest(meta, stream(junit), stream(jacoco));
	}

	@Test
	void exercisesAllReportServicesAgainstRealContext() {
		// Baseline (c1): both pass, 80% coverage. Then c2: 'a' fails, 'b' slower, 75%
		// cov.
		TestRun base = ingest("c1", junit(false, 0.30, 0.20), jacoco(8, 2));
		TestRun current = ingest("c2", junit(true, 0.30, 0.50), jacoco(6, 2));
		// Same commit as the baseline but 'a' now fails -> a flip for flaky detection.
		ingest("c1", junit(true, 0.30, 0.20), jacoco(8, 2));

		Long projectId = base.getProject().getId();
		Long currentId = current.getId();

		// ReportingService read side.
		assertThat(reporting.listProjects()).isNotEmpty();
		assertThat(reporting.findProject(projectId)).isPresent();
		assertThat(reporting.findProjectByName("core-demo")).isPresent();
		assertThat(reporting.runCount(projectId)).isGreaterThanOrEqualTo(3);
		assertThat(reporting.recentRuns(projectId, 10)).isNotEmpty();
		assertThat(reporting.trendRuns(projectId, 10)).isNotEmpty();
		assertThat(reporting.findRun(currentId)).isPresent();
		assertThat(reporting.latestRunByCommit(projectId, "c2", null)).isPresent();
		assertThat(reporting.latestRunByBranch(projectId, "main", "default")).isPresent();
		assertThat(reporting.flagSummaries(projectId)).isNotEmpty();
		assertThat(reporting.suitesFor(currentId)).isNotEmpty();
		List<TestCaseResult> failures = reporting.failedCasesFor(currentId);
		assertThat(failures).isNotEmpty();
		assertThat(reporting.allCasesFor(currentId)).isNotEmpty();
		assertThat(reporting.coverageFor(currentId)).isPresent();
		reporting.coverageFor(currentId)
			.ifPresent((cov) -> assertThat(reporting.coverageFiles(cov.getId(), 50)).isNotNull());

		// Quality gate: a new failure and a coverage drop -> fails.
		assertThat(gate.evaluate(currentId)).hasValueSatisfying((r) -> assertThat(r.passed()).isFalse());
		assertThat(gate.coverageDelta(currentId)).isPresent();

		// Global health board: core-demo appears with its latest gate status + flaky
		// count.
		List<ProjectHealth> board = projectHealth.board();
		ProjectHealth demo = board.stream().filter((h) -> projectId.equals(h.projectId())).findFirst().orElseThrow();
		assertThat(demo.hasRuns()).isTrue();
		assertThat(demo.gateStatus()).isIn("PASSED", "FAILED");
		assertThat(demo.passRate()).isNotNull();
		assertThat(demo.projectName()).isEqualTo("core-demo");
		assertThat(demo.flakyCount()).isGreaterThanOrEqualTo(1);

		// Regression diff: 'a' is a new failure vs the baseline.
		assertThat(regression.diff(currentId)).hasValueSatisfying((r) -> {
			assertThat(r.baselineFound()).isTrue();
			assertThat(r.newFailureCount()).isGreaterThanOrEqualTo(1);
		});

		// Performance regression: 'b' went 200ms -> 500ms.
		assertThat(perfRegression.diff(currentId))
			.hasValueSatisfying((r) -> assertThat(r.slowerCount()).isGreaterThanOrEqualTo(1));

		// Blame: the failing test's streak began at the current run.
		assertThat(blame.blame(currentId)).isNotEmpty();
		assertThat(blame.blameByCaseId(current, failures)).isNotNull();

		// Performance views.
		assertThat(performance.slowestInRun(currentId, 10)).isNotEmpty();
		assertThat(performance.suiteTimeTrend(projectId, 30)).isNotEmpty();
		assertThat(performance.summary(projectId, 25, 30).slowestInLatestRun()).isNotNull();
		assertThat(performance.testDurationTrend(projectId, "com.x.G", "a", 30).points()).isNotEmpty();

		// Flaky management.
		flaky.setStatus(projectId, "com.x.G", "a", FlakyStatus.QUARANTINED, "investigating");
		assertThat(flaky.listFlaky(projectId)).isNotNull();

		// Failure clustering.
		assertThat(clustering.cluster(projectId)).isNotNull();

		// Triage rules + run categorisation.
		triage.addRule(projectId, "assertions", "product-bug", "AssertionError", 1);
		assertThat(triage.listRules(projectId)).isNotEmpty();
		assertThat(triage.triageRun(currentId)).isNotNull();
		assertThat(triage.categoryByCaseId(projectId, failures)).isNotNull();

		// Per-project settings: effective config + persistence.
		assertThat(settings.globals().baseBranch()).isEqualTo("main");
		settings.save(projectId, "main", 80.0, 0.5, true, true, "ci/gate", false);
		assertThat(settings.find(projectId)).isPresent();
		assertThat(settings.gateConfig(projectId).minLineCoverage()).isEqualTo(80.0);
	}

	@Test
	void ingestsJmeterPerfRunWithPerLabelRows() {
		byte[] jtl = ("timeStamp,elapsed,label,success\n" + "1000,100,GET /a,true\n" + "1100,300,GET /a,false\n"
				+ "1200,50,GET /b,true\n")
			.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		IngestRequest meta = new IngestRequest("perf-demo", null, "main", "default", "c1", null, null, null);
		var run = this.perfIngest.ingest(meta, List.of(() -> new ByteArrayInputStream(jtl)));

		assertThat(run.getId()).isNotNull();
		assertThat(run.getFormat()).isEqualTo("jmeter");
		assertThat(run.getSampleCount()).isEqualTo(3);
		assertThat(run.getErrorCount()).isEqualTo(1);
		assertThat(run.getP95Ms()).isGreaterThan(0);
		assertThat(run.isOk()).isFalse();
		assertThat(this.perfTransactions.findByPerfRunIdOrderByMeanMsDesc(run.getId())).hasSize(2);

		Long projectId = run.getProject().getId();
		assertThat(this.reporting.recentPerfRuns(projectId, 10)).isNotEmpty();
		assertThat(this.reporting.perfTrend(projectId, 30)).isNotEmpty();
		assertThat(this.reporting.perfTrend(projectId, 30).get(0).p95Ms()).isGreaterThan(0);
	}

	@Test
	void perfRunRegressionFlagsLatencyAndErrorsVsBaseline() {
		byte[] good = "timeStamp,elapsed,label,success\n1000,100,GET /a,true\n1100,100,GET /a,true\n1200,100,GET /a,true\n"
			.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		byte[] bad = "timeStamp,elapsed,label,success\n1000,500,GET /a,false\n1100,500,GET /a,true\n1200,500,GET /a,true\n"
			.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		var baseline = this.perfIngest.ingest(
				new IngestRequest("perfreg", null, "main", "default", "b", null, null, null),
				List.of(() -> new ByteArrayInputStream(good)));
		var current = this.perfIngest.ingest(
				new IngestRequest("perfreg", null, "main", "default", "c", null, null, null),
				List.of(() -> new ByteArrayInputStream(bad)));

		// Baseline run has no prior run -> no regression.
		assertThat(this.perfRunRegression.evaluate(baseline.getId())).hasValueSatisfying((r) -> {
			assertThat(r.baselineFound()).isFalse();
			assertThat(r.passed()).isTrue();
		});
		// Current: p95 100->500ms and 33% errors -> regressed on latency + error rate.
		assertThat(this.perfRunRegression.evaluate(current.getId())).hasValueSatisfying((r) -> {
			assertThat(r.baselineFound()).isTrue();
			assertThat(r.regressed()).isTrue();
			assertThat(r.rules()).anyMatch((rule) -> rule.name().equals("latency-p95") && !rule.passed());
			assertThat(r.rules()).anyMatch((rule) -> rule.name().equals("error-rate") && !rule.passed());
		});
	}

	@Test
	void perfRunDetailExposesPerLabelRowsAndBaselineDelta() {
		byte[] good = "timeStamp,elapsed,label,success\n1000,100,GET /a,true\n1100,100,GET /a,true\n1200,80,GET /b,true\n"
			.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		byte[] worse = "timeStamp,elapsed,label,success\n1000,300,GET /a,true\n1100,300,GET /a,false\n1200,80,GET /b,true\n"
			.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		var baseline = this.perfIngest.ingest(
				new IngestRequest("perfdetail", null, "main", "default", "b", null, null, null),
				List.of(() -> new ByteArrayInputStream(good)));
		var current = this.perfIngest.ingest(
				new IngestRequest("perfdetail", null, "main", "default", "c", null, null, null),
				List.of(() -> new ByteArrayInputStream(worse)));

		assertThat(this.perfRunDetail.detail(-1L)).isEmpty();
		assertThat(this.perfRunDetail.detail(baseline.getId())).hasValueSatisfying((d) -> {
			assertThat(d.projectName()).isEqualTo("perfdetail");
			assertThat(d.labels()).hasSize(2);
			// No prior run -> no baseline delta.
			assertThat(d.labels()).allMatch((row) -> row.p95DeltaPct() == null);
			assertThat(d.regression()).isNotNull();
		});
		assertThat(this.perfRunDetail.detail(current.getId())).hasValueSatisfying((d) -> {
			assertThat(d.runId()).isEqualTo(current.getId());
			assertThat(d.labels()).hasSize(2);
			// GET /a went 100->300ms vs the baseline -> positive p95 delta.
			assertThat(d.labels()).anyMatch(
					(row) -> row.label().equals("GET /a") && row.baselineP95Ms() != null && row.p95DeltaPct() > 0);
			assertThat(d.regression().regressed()).isTrue();
		});
	}

}
