package org.alexmond.unitrack.ingest;

import org.alexmond.unitrack.domain.CoverageFileEntry;
import org.alexmond.unitrack.domain.CoverageReport;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.TestSuiteResult;
import org.alexmond.unitrack.domain.Visibility;
import org.alexmond.unitrack.repository.CoverageFileEntryRepository;
import org.alexmond.unitrack.repository.CoverageReportRepository;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.alexmond.unitrack.repository.TestCaseResultRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.alexmond.unitrack.repository.TestSuiteResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Orchestrates parsing and persistence of an uploaded set of JUnit + JaCoCo reports. */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngestService {

	private final ProjectRepository projects;

	private final TestRunRepository runs;

	private final TestSuiteResultRepository suites;

	private final TestCaseResultRepository cases;

	private final CoverageReportRepository coverageReports;

	private final CoverageFileEntryRepository coverageFiles;

	private final JUnitXmlParser junitParser;

	private final CoverageParsers coverageParsers;

	/**
	 * Visibility assigned to auto-created projects on first ingest. Defaults to PRIVATE.
	 */
	@Value("${unitrack.security.default-visibility:PRIVATE}")
	private Visibility defaultVisibility = Visibility.PRIVATE;

	/**
	 * Parses and stores a run. {@code junitStreams} and {@code jacocoStreams} are
	 * suppliers of input streams so the caller controls resource lifecycle; at least one
	 * JUnit stream is required.
	 */
	@Transactional
	public TestRun ingest(IngestRequest meta, List<Supplier<InputStream>> junitStreams,
			List<Supplier<InputStream>> jacocoStreams) {
		if (meta.project() == null || meta.project().isBlank()) {
			throw new IngestException("'project' is required");
		}
		if (junitStreams.isEmpty()) {
			throw new IngestException("At least one JUnit XML file is required");
		}

		Project project = findOrCreateProject(meta.project(), meta.repoUrl());
		String runKey = blankToNull(meta.runKey());
		TestRun existing = (runKey != null) ? runs.findByProjectIdAndRunKey(project.getId(), runKey).orElse(null)
				: null;
		boolean merging = existing != null;

		JUnitResults merged = parseJUnit(junitStreams);
		TestRun run;
		if (merging) {
			run = existing;
			run.addTotals(merged.passed(), merged.failures(), merged.errors(), merged.skipped(), merged.durationMs());
		}
		else {
			run = new TestRun(project, blankToNull(meta.branch()), meta.flag(), blankToNull(meta.commit()),
					blankToNull(meta.buildUrl()), blankToNull(meta.ciProvider()));
			run.setBuildName(blankToNull(meta.buildName()));
			run.setRunKey(runKey);
			run.setBaseBranch(blankToNull(meta.baseBranch()));
			run.setPrNumber(meta.prNumber());
			run.applyTotals(merged.passed(), merged.failures(), merged.errors(), merged.skipped(), merged.durationMs());
		}
		runs.save(run);

		persistTests(run, merged);

		if (!jacocoStreams.isEmpty()) {
			persistCoverage(run, parseCoverage(jacocoStreams));
			runs.save(run);
		}

		log.info("{} run {} for project '{}' ({} tests, {} failed, {} errors, {} uploads)",
				merging ? "Merged into" : "Ingested", run.getId(), project.getName(), run.getTotalTests(),
				run.getFailed(), run.getErrors(), run.getUploads());
		return run;
	}

	private Project findOrCreateProject(String name, String repoUrl) {
		return projects.findByName(name).map((existing) -> {
			if (repoUrl != null && !repoUrl.isBlank() && existing.getRepoUrl() == null) {
				existing.setRepoUrl(repoUrl);
			}
			return existing;
		}).orElseGet(() -> {
			Project created = new Project(name, blankToNull(repoUrl));
			created.setVisibility(defaultVisibility);
			return projects.save(created);
		});
	}

	private JUnitResults parseJUnit(List<Supplier<InputStream>> streams) {
		List<ParsedSuite> all = new ArrayList<>();
		for (Supplier<InputStream> supplier : streams) {
			try (InputStream in = supplier.get()) {
				all.addAll(junitParser.parse(in).suites());
			}
			catch (IOException ex) {
				throw new IngestException("Failed reading JUnit upload: " + ex.getMessage(), ex);
			}
		}
		return new JUnitResults(all);
	}

	private void persistTests(TestRun run, JUnitResults merged) {
		List<TestSuiteResult> suiteRows = new ArrayList<>();
		List<TestCaseResult> caseRows = new ArrayList<>();
		for (ParsedSuite suite : merged.suites()) {
			suiteRows.add(new TestSuiteResult(run, suite.name(), suite.tests(), suite.failures(), suite.errors(),
					suite.skipped(), suite.durationMs()));
			for (ParsedCase c : suite.cases()) {
				TestCaseResult row = new TestCaseResult(run, c.suiteName(), c.className(), c.name(), c.status(),
						c.durationMs());
				if (c.failureMessage() != null || c.failureStacktrace() != null || c.failureType() != null) {
					row.setFailure(c.failureType(), c.failureMessage(), c.failureStacktrace());
				}
				if (c.systemOut() != null || c.systemErr() != null || !c.attachments().isEmpty()) {
					row.setOutputs(c.systemOut(), c.systemErr(), c.attachments());
				}
				caseRows.add(row);
			}
		}
		suites.saveAll(suiteRows);
		cases.saveAll(caseRows);
	}

	private CoverageResults parseCoverage(List<Supplier<InputStream>> streams) {
		// Merge multiple coverage reports by summing counters and concatenating file
		// rows.
		int lc = 0;
		int lm = 0;
		int bc = 0;
		int bm = 0;
		int ic = 0;
		int im = 0;
		int mc = 0;
		int mm = 0;
		List<CoverageResults.ParsedFileCoverage> files = new ArrayList<>();
		for (Supplier<InputStream> supplier : streams) {
			try (InputStream in = supplier.get()) {
				CoverageResults r = coverageParsers.parse(in);
				lc += r.lineCovered();
				lm += r.lineMissed();
				bc += r.branchCovered();
				bm += r.branchMissed();
				ic += r.instructionCovered();
				im += r.instructionMissed();
				mc += r.methodCovered();
				mm += r.methodMissed();
				files.addAll(r.files());
			}
			catch (IOException ex) {
				throw new IngestException("Failed reading coverage upload: " + ex.getMessage(), ex);
			}
		}
		return new CoverageResults(lc, lm, bc, bm, ic, im, mc, mm, files);
	}

	private void persistCoverage(TestRun run, CoverageResults cov) {
		// Merge into an existing report (sharded coverage uploads) or create a new one.
		CoverageReport report = coverageReports.findByRunId(run.getId()).orElseGet(() -> new CoverageReport(run));
		if (report.getId() == null) {
			report.setCounters(cov.lineCovered(), cov.lineMissed(), cov.branchCovered(), cov.branchMissed(),
					cov.instructionCovered(), cov.instructionMissed(), cov.methodCovered(), cov.methodMissed());
		}
		else {
			report.addCounters(cov.lineCovered(), cov.lineMissed(), cov.branchCovered(), cov.branchMissed(),
					cov.instructionCovered(), cov.instructionMissed(), cov.methodCovered(), cov.methodMissed());
		}
		coverageReports.save(report);

		List<CoverageFileEntry> fileRows = new ArrayList<>();
		for (CoverageResults.ParsedFileCoverage f : cov.files()) {
			fileRows.add(new CoverageFileEntry(report, f.packageName(), f.fileName(), f.lineCovered(), f.lineMissed(),
					f.branchCovered(), f.branchMissed()));
		}
		coverageFiles.saveAll(fileRows);

		run.setLineCoveragePct(report.getLinePct());
		run.setBranchCoveragePct(report.getBranchPct());
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}

}
