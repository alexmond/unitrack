package org.alexmond.unitrack.report;

import java.util.List;

import org.alexmond.unitrack.domain.CoverageFileEntry;
import org.alexmond.unitrack.repository.CoverageFileEntryRepository;
import org.alexmond.unitrack.repository.CoverageReportRepository;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.alexmond.unitrack.repository.TestCaseResultRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.alexmond.unitrack.repository.TestSuiteResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Modules are derived from the package tree of a coverage report (no module concept at
 * ingest): the segment after the longest package prefix common to every file becomes the
 * module.
 */
@ExtendWith(MockitoExtension.class)
class ModuleCoverageTest {

	@Mock
	private ProjectRepository projects;

	@Mock
	private TestRunRepository runs;

	@Mock
	private CoverageReportRepository coverageReports;

	@Mock
	private CoverageFileEntryRepository coverageFiles;

	@Mock
	private TestCaseResultRepository testCases;

	@Mock
	private TestSuiteResultRepository suites;

	@InjectMocks
	private ReportingService reporting;

	private static CoverageFileEntry file(String pkg, int covered, int missed) {
		return new CoverageFileEntry(null, pkg, "X.java", covered, missed, 0, 0);
	}

	@Test
	void groupsByModuleSegmentAfterCommonPrefix() {
		given(coverageFiles.findByReportIdOrderByLineMissedDescPackageNameAsc(1L))
			.willReturn(List.of(file("org/alexmond/builder/api", 10, 90), file("org/alexmond/builder/api/sub", 5, 5),
					file("org/alexmond/builder/core", 80, 20), file("org/alexmond/builder/web", 0, 50)));

		List<ModuleCoverage> mods = reporting.moduleCoverage(1L);

		// Common prefix is org/alexmond/builder; the next segment is the module.
		assertThat(mods).extracting(ModuleCoverage::name).containsExactly("api", "core", "web");
		ModuleCoverage api = mods.stream().filter((m) -> m.name().equals("api")).findFirst().orElseThrow();
		assertThat(api.files()).isEqualTo(2);
		assertThat(api.lineCovered()).isEqualTo(15);
		assertThat(api.lineTotal()).isEqualTo(110);
	}

	@Test
	void singleModuleCollapsesToOneEntrySoTheViewCanHide() {
		given(coverageFiles.findByReportIdOrderByLineMissedDescPackageNameAsc(2L))
			.willReturn(List.of(file("com/example/app", 1, 1), file("com/example/app", 2, 0)));

		assertThat(reporting.moduleCoverage(2L)).hasSize(1);
	}

}
