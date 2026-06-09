package org.alexmond.unitrack.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Expands report glob patterns (e.g. {@code target/surefire-reports/TEST-*.xml}) into
 * existing files.
 */
@Component
class ReportResolver {

	private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

	/**
	 * Resolves all patterns to readable, existing files (de-duplicated,
	 * order-preserving).
	 */
	List<Resource> resolve(List<String> patterns) {
		List<Resource> out = new ArrayList<>();
		if (patterns == null) {
			return out;
		}
		for (String pattern : patterns) {
			if (pattern == null || pattern.isBlank()) {
				continue;
			}
			try {
				for (Resource r : this.resolver.getResources(toLocation(pattern))) {
					if (r.exists() && r.isReadable() && !out.contains(r)) {
						out.add(r);
					}
				}
			}
			catch (IOException ex) {
				// A pattern that matches nothing simply contributes no files.
				continue;
			}
		}
		return out;
	}

	private static String toLocation(String pattern) {
		if (pattern.startsWith("file:") || pattern.startsWith("classpath:")) {
			return pattern;
		}
		return "file:" + pattern;
	}

}
