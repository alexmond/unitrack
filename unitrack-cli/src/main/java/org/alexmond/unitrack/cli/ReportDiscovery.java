package org.alexmond.unitrack.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/**
 * Sonar-style zero-config discovery: walk the project tree for well-known
 * test/coverage/perf report files, confirm each one's format by sniffing its content (so
 * a stray XML in a reports directory isn't uploaded as if it were a test report), and
 * bucket them by kind.
 *
 * <p>
 * Like sonar-scanner, conventional locations are checked automatically and the include /
 * exclude globs are ant-style ({@code **}, {@code *}, {@code ?}). Reports are imported,
 * never generated.
 */
@Component
class ReportDiscovery {

	/** Conventional report locations (Maven + Gradle + common JS/.NET/Python/Go). */
	private static final List<String> DEFAULT_GLOBS = List.of("**/target/surefire-reports/*.xml",
			"**/target/failsafe-reports/*.xml", "**/build/test-results/**/*.xml", "**/target/site/jacoco/*.xml",
			"**/build/reports/jacoco/**/*.xml", "**/target/site/cobertura/*.xml", "**/cobertura*.xml",
			"**/coverage.xml", "**/lcov.info", "**/*.opencover.xml", "**/*.jtl");

	/** Always skipped — dependency/vcs trees that never hold the build's own reports. */
	private static final List<String> DEFAULT_EXCLUDES = List.of("**/node_modules/**", "**/.git/**", "**/vendor/**",
			"**/.venv/**");

	/**
	 * Sniff window: report headers (root element, lcov prefixes, CSV header) live up
	 * front.
	 */
	private static final int SNIFF_BYTES = 16 * 1024;

	private final ReportResolver resolver;

	private final AntPathMatcher matcher = new AntPathMatcher();

	ReportDiscovery(ReportResolver resolver) {
		this.resolver = resolver;
	}

	/**
	 * Discovers reports under {@code root}. {@code includes} (when non-empty) replace the
	 * default conventional locations; {@code excludes} are applied on top of the built-in
	 * excludes. Patterns are relative to {@code root}.
	 */
	Discovered discover(String root, List<String> includes, List<String> excludes) {
		List<String> globs = (includes == null || includes.isEmpty()) ? DEFAULT_GLOBS : includes;
		List<String> patterns = globs.stream().map((g) -> join(root, g)).toList();
		List<Resource> candidates = this.resolver.resolve(patterns);

		List<String> excludePatterns = new ArrayList<>(DEFAULT_EXCLUDES);
		if (excludes != null) {
			excludePatterns.addAll(excludes);
		}

		List<Resource> junit = new ArrayList<>();
		List<Resource> coverage = new ArrayList<>();
		List<Resource> perf = new ArrayList<>();
		List<String> skipped = new ArrayList<>();
		for (Resource r : candidates) {
			String path = pathOf(r);
			if (isExcluded(path, excludePatterns)) {
				continue;
			}
			Kind kind = classify(r);
			if (kind == null) {
				skipped.add(nameOf(r) + " (unrecognized format)");
				continue;
			}
			switch (kind) {
				case JUNIT -> junit.add(r);
				case COVERAGE -> coverage.add(r);
				case PERF -> perf.add(r);
			}
		}
		return new Discovered(junit, coverage, perf, skipped);
	}

	private boolean isExcluded(String path, List<String> excludePatterns) {
		String normalized = path.replace('\\', '/');
		for (String ex : excludePatterns) {
			if (this.matcher.match(ex, normalized) || normalized.contains(stripGlob(ex))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A directory token from an ant glob like {@code ** /node_modules/ **} for a cheap
	 * contains-check.
	 */
	private static String stripGlob(String pattern) {
		return pattern.replace("**/", "").replace("/**", "").replace("*", "");
	}

	/** Sniffs the head of a file and returns the report kind it actually is, or null. */
	Kind classify(Resource r) {
		String head = head(r);
		if (head == null || head.isBlank()) {
			return null;
		}
		String h = head.toLowerCase(Locale.ROOT);
		if (h.contains("<testsuite")) {
			return Kind.JUNIT;
		}
		if (h.contains("<testresults") || isCsvJtl(head)) {
			return Kind.PERF;
		}
		if (h.contains("<report") && h.contains("<counter")) {
			return Kind.COVERAGE; // JaCoCo
		}
		if (h.contains("<coverage") || h.contains("<coveragesession")) {
			return Kind.COVERAGE; // Cobertura / Clover / OpenCover
		}
		if (isLcov(head)) {
			return Kind.COVERAGE;
		}
		return null;
	}

	private static boolean isCsvJtl(String head) {
		int nl = head.indexOf('\n');
		String first = (nl >= 0) ? head.substring(0, nl) : head;
		String lower = first.toLowerCase(Locale.ROOT);
		return lower.contains("timestamp") && lower.contains("elapsed");
	}

	private static boolean isLcov(String head) {
		return head.lines().anyMatch((l) -> l.startsWith("SF:") || l.startsWith("TN:") || l.startsWith("DA:"));
	}

	private static String head(Resource r) {
		try (InputStream in = r.getInputStream()) {
			byte[] buf = in.readNBytes(SNIFF_BYTES);
			return new String(buf, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			return null;
		}
	}

	private static String pathOf(Resource r) {
		try {
			return r.getURI().getPath();
		}
		catch (IOException | RuntimeException ex) {
			String name = r.getFilename();
			return (name != null) ? name : "";
		}
	}

	private static String nameOf(Resource r) {
		String name = r.getFilename();
		return (name != null) ? name : "file";
	}

	private static String join(String root, String glob) {
		if (root == null || root.isBlank() || ".".equals(root)) {
			return glob;
		}
		String base = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
		return base + "/" + glob;
	}

	/** The kind of report a file turned out to be, after sniffing its content. */
	enum Kind {

		JUNIT, COVERAGE, PERF

	}

	/**
	 * Discovered reports bucketed by kind, plus the files that were looked at but
	 * skipped.
	 */
	record Discovered(List<Resource> junit, List<Resource> coverage, List<Resource> perf, List<String> skipped) {

		int total() {
			return this.junit.size() + this.coverage.size() + this.perf.size();
		}
	}

}
