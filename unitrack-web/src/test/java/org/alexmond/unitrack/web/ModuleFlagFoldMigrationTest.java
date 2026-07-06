package org.alexmond.unitrack.web;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestCaseResult;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.TestStatus;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.alexmond.unitrack.repository.TestCaseResultRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the V25 fold logic: per-module flag-runs collapse into their build's {@code
 * default} rollup run, with the rollup's cases tagged by module and the duplicate module
 * runs removed — while a module-shaped flag with NO default sibling (a real coverage
 * component) is left untouched. V25 itself runs at startup against an empty H2, so this
 * re-applies the migration SQL to a seeded split shape to exercise the transform.
 */
@SpringBootTest
class ModuleFlagFoldMigrationTest {

	@Autowired
	private ProjectRepository projects;

	@Autowired
	private TestRunRepository runs;

	@Autowired
	private TestCaseResultRepository cases;

	@Autowired
	private JdbcTemplate jdbc;

	private TestRun run(Project p, String flag, String commit) {
		TestRun r = new TestRun(p, "main", flag, commit, null, null);
		r.applyTotals(0, 0, 0, 0, 0);
		return runs.save(r);
	}

	private void seedCase(TestRun r, String name) {
		cases.save(new TestCaseResult(r, "com.x.Suite", "com.x.T", name, TestStatus.PASSED, 1));
	}

	private void applyV25() throws Exception {
		String sql = StreamUtils.copyToString(
				new ClassPathResource("db/migration/V25__fold_module_flags_into_run.sql").getInputStream(),
				StandardCharsets.UTF_8);
		// Drop full-line comments before splitting so a ';' inside a comment can't cut a
		// statement (Flyway is comment-aware; this hand-rolled splitter is not).
		String stripped = sql.lines().filter((l) -> !l.strip().startsWith("--")).reduce("", (a, b) -> a + "\n" + b);
		for (String raw : stripped.split(";")) {
			String stmt = raw.strip();
			if (!stmt.isEmpty()) {
				jdbc.execute(stmt);
			}
		}
	}

	@Test
	void foldsModuleRunsIntoRollupAndTagsCases() throws Exception {
		Project p = projects.save(new Project("fold-demo", null));
		String commit = "sha-fold-1";
		// Rollup: the whole build (A,B,C), untagged.
		TestRun rollup = run(p, "default", commit);
		seedCase(rollup, "A");
		seedCase(rollup, "B");
		seedCase(rollup, "C");
		// Module runs: the same tests, partitioned (duplicates of the rollup).
		TestRun modX = run(p, "fold-demo-x", commit);
		seedCase(modX, "A");
		seedCase(modX, "B");
		TestRun modY = run(p, "fold-demo-y", commit);
		seedCase(modY, "C");

		applyV25();

		// Module runs are gone; the rollup survives with all three cases.
		assertThat(runs.findById(modX.getId())).isEmpty();
		assertThat(runs.findById(modY.getId())).isEmpty();
		assertThat(runs.findById(rollup.getId())).isPresent();
		List<TestCaseResult> rollupCases = cases.findByRunIdOrderByStatusAscClassNameAscNameAsc(rollup.getId());
		assertThat(rollupCases).hasSize(3);
		assertThat(rollupCases).allSatisfy((c) -> {
			String want = "C".equals(c.getName()) ? "fold-demo-y" : "fold-demo-x";
			assertThat(c.getModule()).isEqualTo(want);
		});
	}

	@Test
	void leavesAComponentFlagWithNoDefaultSiblingUntouched() throws Exception {
		Project p = projects.save(new Project("component-demo", null));
		// A single component flag, no 'default' rollup at the commit — must NOT be
		// folded.
		TestRun frontend = run(p, "frontend", "sha-comp-1");
		seedCase(frontend, "A");

		applyV25();

		assertThat(runs.findById(frontend.getId())).isPresent();
		List<TestCaseResult> c = cases.findByRunIdOrderByStatusAscClassNameAscNameAsc(frontend.getId());
		assertThat(c).hasSize(1);
		assertThat(c.get(0).getModule()).isNull();
	}

}
