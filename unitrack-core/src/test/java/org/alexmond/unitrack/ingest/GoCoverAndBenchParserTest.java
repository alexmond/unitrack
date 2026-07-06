package org.alexmond.unitrack.ingest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Go {@code cover.out} (coverage) and {@code go test -bench} (perf) parsers. */
class GoCoverAndBenchParserTest {

	private static ByteArrayInputStream in(String s) {
		return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
	}

	private static final String COVER = """
			mode: set
			shop/cart/cart.go:10.20,12.3 2 1
			shop/cart/cart.go:14.2,16.4 3 0
			shop/cart/order.go:5.1,7.2 1 1
			""";

	private static final String BENCH = """
			goos: linux
			goarch: amd64
			pkg: shop/cart
			BenchmarkRender-8   	 1000000	      1234 ns/op	     512 B/op	       3 allocs/op
			BenchmarkParse-8    	  500000	      2500 ns/op
			PASS
			""";

	@Test
	void goCoverParsesStatementsAndDetects() {
		GoCoverParser parser = new GoCoverParser();
		assertThat(parser.supports(COVER)).isTrue();
		CoverageResults r = parser.parse(in(COVER));
		assertThat(r.lineCovered()).isEqualTo(3); // 2 + 1 covered statements
		assertThat(r.lineMissed()).isEqualTo(3); // the count=0 block
		assertThat(r.files()).hasSize(2);
		CoverageResults.ParsedFileCoverage cart = r.files()
			.stream()
			.filter((f) -> f.fileName().equals("cart.go"))
			.findFirst()
			.orElseThrow();
		assertThat(cart.packageName()).isEqualTo("shop/cart");
		assertThat(cart.lineCovered()).isEqualTo(2);
		assertThat(cart.lineMissed()).isEqualTo(3);
	}

	@Test
	void goBenchParsesNsPerOpAndDetects() {
		GoBenchParser parser = new GoBenchParser();
		assertThat(parser.supports(BENCH)).isTrue();
		PerfResults r = parser.parse(in(BENCH));
		assertThat(r.labels()).hasSize(2);
		PerfResults.LabelStats render = r.labels()
			.stream()
			.filter((l) -> l.label().equals("BenchmarkRender-8"))
			.findFirst()
			.orElseThrow();
		assertThat(render.meanMs()).isCloseTo(0.001234, within(1e-9)); // 1234 ns/op
		assertThat(render.sampleCount()).isEqualTo(1_000_000);
		assertThat(r.sampleCount()).isEqualTo(1_500_000);
	}

}
