package org.alexmond.unitrack.web.scm;

import java.util.List;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.report.QualityGateResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The one-line status description both providers post. */
class GateSummaryTest {

	private TestRun run(Double coverage) {
		TestRun run = new TestRun(new Project("demo", "https://github.com/octo/repo"), "main", "default", "abc123",
				null, null);
		run.applyTotals(3, 1, 0, 0, 100);
		run.setLineCoveragePct(coverage);
		return run;
	}

	@Test
	void describesGateTestsAndCoverageWithDelta() {
		String s = GateSummary.describe(run(80.0), new QualityGateResult(true, List.of()), 1.25, 140);
		assertThat(s).isEqualTo("Gate PASSED · 3 passed, 1 failed · cov 80.0% (+1.3pp)");
	}

	@Test
	void omitsCoverageWhenAbsentAndDeltaWhenThereIsNoBaseline() {
		assertThat(GateSummary.describe(run(null), new QualityGateResult(false, List.of()), 1.5, 140))
			.isEqualTo("Gate FAILED · 3 passed, 1 failed");
		assertThat(GateSummary.describe(run(80.0), new QualityGateResult(true, List.of()), null, 140))
			.isEqualTo("Gate PASSED · 3 passed, 1 failed · cov 80.0%");
	}

	@Test
	void reportsNoGateWhenNoneIsConfigured() {
		assertThat(GateSummary.describe(run(null), null, null, 140)).startsWith("Gate n/a · ");
	}

	@Test
	void truncatesToTheProviderLimit() {
		// GitHub caps a status description at 140, GitLab at 255 — same sentence, and the
		// caller owns the limit.
		String s = GateSummary.describe(run(80.0), new QualityGateResult(true, List.of()), 1.25, 11);
		assertThat(s).isEqualTo("Gate PASSED");
	}

}
