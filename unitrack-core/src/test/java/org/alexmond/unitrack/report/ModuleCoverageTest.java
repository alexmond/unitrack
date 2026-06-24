package org.alexmond.unitrack.report;

import java.util.List;

import org.alexmond.unitrack.repository.CoverageFileEntryRepository;
import org.alexmond.unitrack.repository.CoverageReportRepository;
import org.alexmond.unitrack.repository.PackageCoverage;
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
 * ingest): the segment after the longest package prefix common to every package becomes
 * the module. The input is the per-package SQL aggregate.
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

	/** One file in a package. */
	private static PackageCoverage pkg(String name, long covered, long missed) {
		return new Pkg(name, covered, missed, 1);
	}

	@Test
	void groupsByModuleSegmentAfterCommonPrefix() {
		given(coverageFiles.aggregateByPackage(1L))
			.willReturn(List.of(pkg("org/alexmond/builder/api", 10, 90), pkg("org/alexmond/builder/api/sub", 5, 5),
					pkg("org/alexmond/builder/core", 80, 20), pkg("org/alexmond/builder/web", 0, 50)));

		List<ModuleCoverage> mods = reporting.moduleCoverage(1L);

		// Common prefix is org/alexmond/builder; the next segment is the module.
		assertThat(mods).extracting(ModuleCoverage::name).containsExactly("api", "core", "web");
		ModuleCoverage api = mods.stream().filter((m) -> m.name().equals("api")).findFirst().orElseThrow();
		assertThat(api.files()).isEqualTo(2);
		assertThat(api.lineCovered()).isEqualTo(15);
		assertThat(api.lineTotal()).isEqualTo(110);
	}

	@Test
	void coveragePackagesScopeToOneModule() {
		given(coverageFiles.aggregateByPackage(1L))
			.willReturn(List.of(pkg("org/alexmond/builder/api", 10, 90), pkg("org/alexmond/builder/api/sub", 5, 5),
					pkg("org/alexmond/builder/core", 80, 20), pkg("org/alexmond/builder/web", 0, 50)));

		// A module drill-down keeps only that module's packages (same derivation as the
		// list).
		assertThat(reporting.coveragePackages(1L, "api")).extracting(CoveragePackage::packageName)
			.containsExactly("org/alexmond/builder/api", "org/alexmond/builder/api/sub");
		// null/blank module = unfiltered.
		assertThat(reporting.coveragePackages(1L, null)).hasSize(4);
	}

	@Test
	void singleModuleCollapsesToOneEntrySoTheViewCanHide() {
		// Files in one package collapse to a single aggregate row → one (root) module.
		given(coverageFiles.aggregateByPackage(2L)).willReturn(List.of(new Pkg("com/example/app", 3, 1, 2)));

		assertThat(reporting.moduleCoverage(2L)).hasSize(1);
	}

	/** Test stand-in for the {@link PackageCoverage} projection. */
	private record Pkg(String packageName, long lineCovered, long lineMissed, long files) implements PackageCoverage {

		@Override
		public String getPackageName() {
			return this.packageName;
		}

		@Override
		public long getLineCovered() {
			return this.lineCovered;
		}

		@Override
		public long getLineMissed() {
			return this.lineMissed;
		}

		@Override
		public long getBranchCovered() {
			return 0;
		}

		@Override
		public long getBranchMissed() {
			return 0;
		}

		@Override
		public long getFiles() {
			return this.files;
		}

	}

}
