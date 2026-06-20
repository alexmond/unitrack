package org.alexmond.unitrack.web;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.ingest.IngestRequest;
import org.alexmond.unitrack.ingest.IngestService;
import org.alexmond.unitrack.report.QualityGateResult;
import org.alexmond.unitrack.report.QualityGateService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per-run read results are cached and invalidated when a run is merged (#280): a repeat
 * gate evaluation is served from cache, and a sharded merge into the same run evicts it.
 */
@SpringBootTest
@Transactional
class GateCacheIntegrationTest {

	@Autowired
	private IngestService ingest;

	@Autowired
	private QualityGateService gate;

	@Autowired
	private CacheManager cacheManager;

	private List<Supplier<InputStream>> junit() throws Exception {
		byte[] xml = getClass().getResourceAsStream("/samples/surefire-sample.xml").readAllBytes();
		return List.of(() -> new ByteArrayInputStream(xml));
	}

	@Test
	void gateIsCachedPerRunAndEvictedOnMerge() throws Exception {
		IngestRequest meta = new IngestRequest("cache-demo", null, "main", null, "c1", null, null, "rk-cache-1");
		TestRun run = this.ingest.ingest(meta, junit(), List.of());
		Long id = run.getId();

		Optional<QualityGateResult> first = this.gate.evaluate(id);
		Optional<QualityGateResult> second = this.gate.evaluate(id);
		// Spring unwraps Optional in the cache, so on a hit the inner result is the very
		// same
		// instance; an uncached call would build a new one each time.
		assertThat(first).isPresent();
		assertThat(second.orElseThrow()).isSameAs(first.orElseThrow());
		assertThat(this.cacheManager.getCache("gate").get(id)).isNotNull();

		// A sharded merge into the same run (same run key) changes its data → cache must
		// drop.
		this.ingest.ingest(new IngestRequest("cache-demo", null, "main", null, "c1", null, null, "rk-cache-1"), junit(),
				List.of());
		assertThat(this.cacheManager.getCache("gate").get(id)).isNull();
	}

}
