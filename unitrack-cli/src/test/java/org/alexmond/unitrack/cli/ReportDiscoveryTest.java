package org.alexmond.unitrack.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ReportDiscoveryTest {

	private final ReportDiscovery discovery = new ReportDiscovery(new ReportResolver());

	private static void write(Path file, String content) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

	@Test
	void discoversAndClassifiesKnownReportsAndSkipsTheRest(@TempDir Path root) throws IOException {
		// Maven module: a surefire report + a JaCoCo report.
		write(root.resolve("modA/target/surefire-reports/TEST-x.xml"), "<testsuite name=\"x\"></testsuite>");
		write(root.resolve("modA/target/site/jacoco/jacoco.xml"),
				"<report name=\"a\"><counter type=\"LINE\" covered=\"1\" missed=\"0\"/></report>");
		// Gradle module: a test-results report.
		write(root.resolve("modB/build/test-results/test/TEST-y.xml"), "<testsuites></testsuites>");
		// A stray XML in a reports dir that isn't a test report — must be skipped.
		write(root.resolve("modA/target/surefire-reports/notatest.xml"), "<something/>");
		// Inside an excluded tree — must be ignored.
		write(root.resolve("node_modules/pkg/coverage.xml"), "<coverage></coverage>");

		ReportDiscovery.Discovered found = this.discovery.discover(root.toString(), List.of(), List.of());

		assertThat(found.junit()).hasSize(2);
		assertThat(found.coverage()).hasSize(1);
		assertThat(found.perf()).isEmpty();
		assertThat(found.skipped()).anyMatch((s) -> s.contains("notatest.xml"));
	}

	@Test
	void classifiesByContentNotName(@TempDir Path root) throws IOException {
		Path lcov = root.resolve("misc.txt");
		Files.writeString(lcov, "TN:\nSF:/src/Foo.java\nDA:1,1\nend_of_record\n");
		assertThat(this.discovery.classify(new org.springframework.core.io.FileSystemResource(lcov)))
			.isEqualTo(ReportDiscovery.Kind.COVERAGE);

		Path jtl = root.resolve("results.csv");
		Files.writeString(jtl, "timeStamp,elapsed,label,responseCode,success\n1,12,home,200,true\n");
		assertThat(this.discovery.classify(new org.springframework.core.io.FileSystemResource(jtl)))
			.isEqualTo(ReportDiscovery.Kind.PERF);
	}

}
