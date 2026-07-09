package org.alexmond.unitrack.ingest;

import java.util.List;

/**
 * Bridges a coverage report's package-relative file path (e.g. {@code org/ex/Foo.java},
 * all a JaCoCo/Cobertura report carries) to the file's repo-relative path (e.g.
 * {@code unitrack-core/src/main/java/org/ex/Foo.java}) by suffix-matching against a
 * source manifest the uploader sends ({@code git ls-files}). Codecov-style: the report
 * never carries the build's source-root prefix, so we recover it from the checkout's real
 * file list. Used to build working GitHub/GitLab source links and PR annotations (#454).
 */
public final class SourcePathResolver {

	private SourcePathResolver() {
	}

	/**
	 * The repo-relative path whose tail matches {@code packagePath}, or null when the
	 * manifest is empty/absent or nothing matches. When a {@code module} is known (#393),
	 * a match under that module directory wins over a bare suffix match elsewhere — so a
	 * class present in two modules resolves to the right one; otherwise the first suffix
	 * match is returned.
	 * @param packagePath the report's package-relative path ({@code org/ex/Foo.java})
	 * @param module the coverage entry's build module, or null
	 * @param manifest repo-relative source paths from the uploader ({@code git ls-files})
	 */
	public static String resolve(String packagePath, String module, List<String> manifest) {
		if (packagePath == null || packagePath.isBlank() || manifest == null || manifest.isEmpty()) {
			return null;
		}
		String suffix = "/" + packagePath;
		String modulePrefix = (module != null && !module.isBlank()) ? (module + "/") : null;
		String first = null;
		for (String f : manifest) {
			if (f == null || !(f.equals(packagePath) || f.endsWith(suffix))) {
				continue;
			}
			if (modulePrefix != null && f.startsWith(modulePrefix)) {
				return f;
			}
			if (first == null) {
				first = f;
			}
		}
		return first;
	}

}
