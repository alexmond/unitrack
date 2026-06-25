package org.alexmond.unitrack.report;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.repository.CoverageFileEntryRepository;
import org.alexmond.unitrack.repository.CoverageReportRepository;
import org.alexmond.unitrack.repository.ShareLinkRepository;
import org.alexmond.unitrack.repository.TestCaseResultRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.alexmond.unitrack.repository.TestSuiteResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hard-deletes a run and everything that hangs off it. The {@code test_run} foreign keys
 * have no {@code ON DELETE CASCADE}, so children are removed explicitly, deepest first
 * (coverage files before their report), inside one transaction. Used to purge bad uploads
 * (e.g. a partial rollup mis-tagged as {@code default} that skews coverage trends).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class RunDeletionService {

	private final TestRunRepository runs;

	private final TestCaseResultRepository cases;

	private final TestSuiteResultRepository suites;

	private final CoverageReportRepository coverageReports;

	private final CoverageFileEntryRepository coverageFiles;

	private final ShareLinkRepository shareLinks;

	public void deleteRun(Long runId) {
		this.coverageFiles.deleteByRunId(runId);
		this.coverageReports.deleteByRunId(runId);
		this.cases.deleteByRunId(runId);
		this.suites.deleteByRunId(runId);
		this.shareLinks.deleteByRunId(runId);
		this.runs.deleteById(runId);
	}

}
