package org.alexmond.unitrack.report;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;

import org.alexmond.unitrack.ingest.IngestRequest;
import org.alexmond.unitrack.ingest.IngestService;
import org.alexmond.unitrack.report.OwnershipService.OwnerScore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cross-project owner board sums each owner's failing/flaky counts across the given
 * projects (#225). Ingest a failing run into two projects, attribute both to the same
 * owner, and check the global scorecard aggregates them.
 */
@SpringBootTest
@Transactional
class OwnershipGlobalScorecardTest {

	@Autowired
	private IngestService ingest;

	@Autowired
	private OwnershipService ownership;

	private List<Supplier<InputStream>> junit() throws Exception {
		byte[] xml = getClass().getResourceAsStream("/samples/surefire-sample.xml").readAllBytes();
		return List.of(() -> new ByteArrayInputStream(xml));
	}

	@Test
	void sumsFailingByOwnerAcrossProjects() throws Exception {
		// surefire-sample.xml: 2 failing cases (one failure + one error) in
		// com.example.CalculatorTest.
		Long a = this.ingest
			.ingest(new IngestRequest("own-alpha", null, "main", null, "c1", null, null, null), junit(), List.of())
			.getProject()
			.getId();
		Long b = this.ingest
			.ingest(new IngestRequest("own-beta", null, "main", null, "c1", null, null, null), junit(), List.of())
			.getProject()
			.getId();
		this.ownership.addRule(a, "team-x", "com.example", 100);
		this.ownership.addRule(b, "team-x", "com.example", 100);

		List<OwnerScore> scores = this.ownership.globalScorecard(List.of(a, b));

		OwnerScore teamX = scores.stream().filter((s) -> "team-x".equals(s.owner())).findFirst().orElseThrow();
		assertThat(teamX.failing()).isEqualTo(4); // 2 failing per project × 2 projects
	}

}
