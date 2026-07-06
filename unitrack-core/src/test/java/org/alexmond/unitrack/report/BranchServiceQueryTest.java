package org.alexmond.unitrack.report;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import jakarta.persistence.EntityManagerFactory;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** The branches list is batched (no per-branch N+1) — #314. */
@SpringBootTest
@Transactional
class BranchServiceQueryTest {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ProjectRepository projects;

	@Autowired
	private TestRunRepository runs;

	@Autowired
	private EntityManagerFactory emf;

	@Test
	void summarisesBranchesDefaultFirstWithCorrectCounts() {
		Project p = projects.save(new Project("br-correct", null));
		run(p, "main", daysAgo(3));
		run(p, "main", daysAgo(1)); // 2 runs on main
		run(p, "feature-x", daysAgo(2)); // 1 run

		List<BranchSummary> branches = this.branchService.list(p.getId());

		assertThat(branches).extracting(BranchSummary::branch).containsExactly("main", "feature-x");
		assertThat(branches.get(0).defaultBranch()).isTrue();
		assertThat(branches.get(0).runCount()).isEqualTo(2);
		assertThat(branches.get(1).runCount()).isEqualTo(1);
	}

	@Test
	void issuesAConstantNumberOfQueriesRegardlessOfBranchCount() {
		Project small = projects.save(new Project("br-2", null));
		run(small, "main", daysAgo(1));
		run(small, "feature-1", daysAgo(1));

		Project big = projects.save(new Project("br-6", null));
		run(big, "main", daysAgo(1));
		for (int i = 1; i <= 5; i++) {
			run(big, "feature-" + i, daysAgo(i));
		}
		this.runs.flush();

		Statistics stats = this.emf.unwrap(SessionFactory.class).getStatistics();
		stats.setStatisticsEnabled(true);
		// Warm both (trigger any lazy default-settings creation) so it doesn't skew the
		// count.
		this.branchService.list(small.getId());
		this.branchService.list(big.getId());
		this.runs.flush();

		stats.clear();
		List<BranchSummary> smallList = this.branchService.list(small.getId());
		long qSmall = stats.getPrepareStatementCount();

		stats.clear();
		List<BranchSummary> bigList = this.branchService.list(big.getId());
		long qBig = stats.getPrepareStatementCount();

		assertThat(smallList).hasSize(2);
		assertThat(bigList).hasSize(6);
		// Constant query count → no per-branch N+1 (was 2N+ before #314).
		assertThat(qBig).isEqualTo(qSmall);
		assertThat(qSmall).isLessThanOrEqualTo(3);
	}

	private void run(Project p, String branch, Instant at) {
		TestRun r = new TestRun(p, branch, "default", "sha", null, null);
		r.applyTotals(10, 0, 0, 0, 100);
		ReflectionTestUtils.setField(r, "createdAt", at);
		this.runs.save(r);
	}

	private Instant daysAgo(int d) {
		return Instant.now().minus(d, ChronoUnit.DAYS);
	}

}
